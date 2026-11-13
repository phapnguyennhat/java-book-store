package com.example.book_store.auth;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
class AuthController {
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String REFRESH_PATH = "/api/auth"; // refresh token is only ever sent to auth endpoints

    private final AuthService auth;
    private final AuthProperties props;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse signup(@Valid @RequestBody SignupRequest r) {
        return UserResponse.of(auth.signup(r));
    }

    @PostMapping("/login")
    UserResponse login(@Valid @RequestBody LoginRequest r, HttpServletResponse res) {
        return setCookies(res, auth.login(r));
    }

    @PostMapping("/refresh")
    // cookies are hidden from the API docs: browsers send them automatically and scripts (Swagger UI) cannot set them
    UserResponse refresh(@Parameter(hidden = true) @CookieValue(name = REFRESH_COOKIE, required = false) String token,
                         HttpServletResponse res) {
        return setCookies(res, auth.refresh(token));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Parameter(hidden = true) @CookieValue(name = SecurityConfig.ACCESS_COOKIE, required = false) String access,
                @Parameter(hidden = true) @CookieValue(name = REFRESH_COOKIE, required = false) String refresh,
                HttpServletResponse res) {
        auth.logout(access, refresh);
        clearCookies(res);
    }

    /** Revokes every refresh token of the caller (all devices). */
    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logoutAll(@AuthenticationPrincipal Jwt jwt, HttpServletResponse res) {
        auth.logoutAll(Long.valueOf(jwt.getSubject()));
        clearCookies(res);
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.of(auth.user(Long.valueOf(jwt.getSubject())));
    }

    private UserResponse setCookies(HttpServletResponse res, AuthService.Session s) {
        add(res, SecurityConfig.ACCESS_COOKIE, s.accessToken(), "/", props.accessTokenTtl());
        add(res, REFRESH_COOKIE, s.refreshToken(), REFRESH_PATH, props.refreshTokenTtl());
        return UserResponse.of(s.user());
    }

    private void clearCookies(HttpServletResponse res) {
        add(res, SecurityConfig.ACCESS_COOKIE, "", "/", Duration.ZERO);
        add(res, REFRESH_COOKIE, "", REFRESH_PATH, Duration.ZERO);
    }

    private void add(HttpServletResponse res, String name, String value, String path, Duration maxAge) {
        res.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value).httpOnly(true).secure(props.cookieSecure())
                .sameSite("Strict").path(path).maxAge(maxAge).build().toString());
    }
}
