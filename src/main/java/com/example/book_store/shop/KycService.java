package com.example.book_store.shop;

import com.example.book_store.storage.FileStorage;
import com.example.book_store.web.ApiErrors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import java.net.URL;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class KycService {
    static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final Duration UPLOAD_TTL = Duration.ofMinutes(10);
    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(5);

    private final ShopService shopService;
    private final ShopKycRepository kycs;
    private final FileStorage storage;
    private final jakarta.validation.Validator validator;

    UploadUrlResponse uploadUrl(Long ownerId, UploadUrlRequest r) {
        shopService.owned(ownerId); // KYC belongs to a shop: create the shop first
        if (!r.type().contentTypes.contains(r.contentType())) {
            throw ApiErrors.of(HttpStatus.BAD_REQUEST, "kyc.content-type", "Content type not allowed for this document",
                    r.contentType(), r.type());
        }
        // the owner's id in the key is what later proves the document was uploaded by this user
        String key = "kyc/%d/%s/%s".formatted(ownerId, r.type(), UUID.randomUUID());
        URL url = storage.uploadUrl(key, r.contentType(), r.size(), UPLOAD_TTL);
        return new UploadUrlResponse(key, url,
                Map.of("Content-Type", r.contentType(), "Content-Length", String.valueOf(r.size())),
                Instant.now().plus(UPLOAD_TTL));
    }

    /**
     * Saves the wizard so far (full replace of the draft). Starts a draft, continues one, or reopens a rejected KYC
     * for editing. Present fields are already validated; document keys are checked here so step 3 fails early.
     * <p>Not one transaction on purpose: the document checks call S3, and no DB transaction is held open across
     * network calls. A concurrent change in between is caught by the version check on save.
     */
    KycResponse saveDraft(Long ownerId, KycRequest r) {
        Shop shop = shopService.owned(ownerId);
        ShopKyc k = kycs.findById(shop.getId()).orElseGet(() -> new ShopKyc(shop.getId()));
        // the admin must approve exactly what they reviewed, so no edits while a review is open
        if (k.getStatus() == KycStatus.PENDING) {
            throw ApiErrors.of(HttpStatus.CONFLICT, "kyc.pending", "KYC is already waiting for review");
        }
        // shortcut: verified data is frozen; changing it later (e.g. a new bank account) needs a re-verification flow
        if (k.getStatus() == KycStatus.APPROVED) {
            throw ApiErrors.of(HttpStatus.CONFLICT, "kyc.approved", "Approved KYC cannot be edited");
        }
        checkDocument(ownerId, DocumentType.ID_FRONT, r.idFrontKey());
        checkDocument(ownerId, DocumentType.ID_BACK, r.idBackKey());
        checkDocument(ownerId, DocumentType.SELFIE, r.selfieKey());
        var b = r.sellerType() == SellerType.INDIVIDUAL ? null : r.business();
        if (b != null) checkDocument(ownerId, DocumentType.BUSINESS_LICENSE, b.licenseKey());
        var bank = r.bank();

        k.setSellerType(r.sellerType());
        k.setLegalName(trim(r.legalName()));
        k.setIdNumber(r.idNumber());
        k.setIdIssueDate(r.idIssueDate());
        k.setIdIssuePlace(trim(r.idIssuePlace()));
        k.setDateOfBirth(r.dateOfBirth());
        k.setPermanentAddress(trim(r.permanentAddress()));
        k.setIdFrontKey(r.idFrontKey());
        k.setIdBackKey(r.idBackKey());
        k.setSelfieKey(r.selfieKey());
        k.setBusinessName(b == null ? null : trim(b.businessName()));
        k.setRegistrationNumber(b == null ? null : trim(b.registrationNumber()));
        k.setRegistrationDate(b == null ? null : b.registrationDate());
        k.setTaxCode(b == null ? null : b.taxCode());
        k.setRegisteredAddress(b == null ? null : trim(b.registeredAddress()));
        k.setBusinessLicenseKey(b == null ? null : b.licenseKey());
        k.setBankCode(bank == null ? null : trim(bank.bankCode()));
        k.setAccountNumber(bank == null ? null : bank.accountNumber());
        k.setAccountHolder(bank == null ? null : trim(bank.accountHolder()));
        k.setStatus(KycStatus.DRAFT); // a rejected KYC keeps its rejection reason visible until resubmitted
        return save(k);
    }

    /** Sends the draft for review: every required field must be there and the payout account must be the seller's. */
    @Transactional
    KycResponse submit(Long ownerId) throws BindException {
        ShopKyc k = find(shopService.owned(ownerId).getId());
        if (k.getStatus() != KycStatus.DRAFT) {
            throw ApiErrors.of(HttpStatus.CONFLICT, "kyc.not-draft", "Only a draft KYC can be submitted");
        }
        KycRequest r = toRequest(k);
        BindingResult errors = new BeanPropertyBindingResult(r, "kycRequest");
        new SpringValidatorAdapter(validator).validate(r, errors, KycRequest.Submit.class);
        if (errors.hasErrors()) throw new BindException(errors); // 400 with the missing fields, like @Valid
        // payouts must go to the seller themselves (or their business), not to a third party
        String holder = normalizeName(k.getAccountHolder());
        if (!holder.equals(normalizeName(k.getLegalName()))
                && (k.getBusinessName() == null || !holder.equals(normalizeName(k.getBusinessName())))) {
            throw ApiErrors.of(HttpStatus.BAD_REQUEST, "kyc.account-holder-mismatch",
                    "Bank account holder must match the legal name or business name");
        }
        k.setStatus(KycStatus.PENDING);
        k.setSubmittedAt(Instant.now());
        k.setReviewedBy(null);
        k.setReviewedAt(null);
        k.setRejectionReason(null);
        return save(k);
    }

    @Transactional(readOnly = true)
    KycResponse mine(Long ownerId) {
        return toResponse(find(shopService.owned(ownerId).getId()));
    }

    @Transactional(readOnly = true)
    PagedModel<KycSummary> list(KycStatus status, Pageable pageable) {
        return new PagedModel<>(kycs.findSummaries(status, pageable));
    }

    @Transactional(readOnly = true)
    KycResponse detail(Long shopId) {
        return toResponse(find(shopId));
    }

    /** Admin decision on a PENDING KYC; a reason is kept only for rejections. */
    KycResponse review(Long shopId, Long adminId, ReviewRequest r) {
        boolean rejected = r.decision() == ReviewRequest.Decision.REJECTED;
        KycStatus decision = rejected ? KycStatus.REJECTED : KycStatus.APPROVED;
        String reason = rejected ? r.reason().trim() : null;
        ShopKyc k = find(shopId);
        if (k.getStatus() != KycStatus.PENDING) {
            throw ApiErrors.of(HttpStatus.CONFLICT, "kyc.not-pending", "KYC is not waiting for review");
        }
        k.setStatus(decision);
        k.setRejectionReason(reason);
        k.setReviewedBy(adminId);
        k.setReviewedAt(Instant.now());
        return save(k);
    }

    /** Writes now (not at commit) so a lost race surfaces here as 409 instead of a 500 after the method returns. */
    private KycResponse save(ShopKyc k) {
        try {
            return toResponse(kycs.saveAndFlush(k));
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
            // another request changed (or first created) this KYC after we read it
            throw ApiErrors.of(HttpStatus.CONFLICT, "kyc.modified", "KYC was changed by another request, reload and try again");
        }
    }

    private ShopKyc find(Long shopId) {
        return kycs.findById(shopId).orElseThrow(
                () -> ApiErrors.of(HttpStatus.NOT_FOUND, "kyc.not-found", "KYC not found"));
    }

    /** A given key must be one this owner was issued for this document type, and the upload must have happened. */
    private void checkDocument(Long ownerId, DocumentType type, String key) {
        if (key == null) return; // not uploaded yet: fine for a draft, caught on submission
        if (!key.matches("kyc/" + ownerId + "/" + type + "/[0-9a-f-]{36}") || !storage.exists(key)) {
            throw ApiErrors.of(HttpStatus.BAD_REQUEST, "kyc.document-invalid", "Document is missing or invalid", type);
        }
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** "Nguyễn Văn  Đức" -> "NGUYEN VAN DUC", the way Vietnamese banks print account holders. */
    static String normalizeName(String s) {
        String noMarks = Normalizer.normalize(s.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noMarks.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private KycRequest toRequest(ShopKyc k) {
        KycRequest.BusinessInfo business = k.getSellerType() == null || k.getSellerType() == SellerType.INDIVIDUAL ? null
                : k.getBusinessName() == null && k.getRegistrationNumber() == null && k.getRegistrationDate() == null
                  && k.getTaxCode() == null && k.getRegisteredAddress() == null && k.getBusinessLicenseKey() == null ? null
                : new KycRequest.BusinessInfo(k.getBusinessName(), k.getRegistrationNumber(), k.getRegistrationDate(),
                        k.getTaxCode(), k.getRegisteredAddress(), k.getBusinessLicenseKey());
        KycRequest.BankAccount bank = k.getBankCode() == null && k.getAccountNumber() == null && k.getAccountHolder() == null
                ? null : new KycRequest.BankAccount(k.getBankCode(), k.getAccountNumber(), k.getAccountHolder());
        return new KycRequest(k.getSellerType(), k.getLegalName(), k.getIdNumber(), k.getIdIssueDate(),
                k.getIdIssuePlace(), k.getDateOfBirth(), k.getPermanentAddress(), k.getIdFrontKey(), k.getIdBackKey(),
                k.getSelfieKey(), business, bank);
    }

    private KycResponse toResponse(ShopKyc k) {
        KycRequest r = toRequest(k);
        Map<DocumentType, URL> docs = new EnumMap<>(DocumentType.class);
        addDocument(docs, DocumentType.ID_FRONT, k.getIdFrontKey());
        addDocument(docs, DocumentType.ID_BACK, k.getIdBackKey());
        addDocument(docs, DocumentType.SELFIE, k.getSelfieKey());
        addDocument(docs, DocumentType.BUSINESS_LICENSE, k.getBusinessLicenseKey());
        return new KycResponse(k.getShopId(), k.getSellerType(), k.getStatus(), k.getLegalName(), k.getIdNumber(),
                k.getIdIssueDate(), k.getIdIssuePlace(), k.getDateOfBirth(), k.getPermanentAddress(), r.business(),
                r.bank(), docs, k.getSubmittedAt(), k.getReviewedAt(), k.getRejectionReason());
    }

    private void addDocument(Map<DocumentType, URL> docs, DocumentType type, String key) {
        if (key != null) docs.put(type, storage.downloadUrl(key, DOWNLOAD_TTL));
    }
}
