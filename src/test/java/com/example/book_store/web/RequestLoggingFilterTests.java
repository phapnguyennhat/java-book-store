package com.example.book_store.web;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain unit test: no Spring context, so it runs without the docker containers. */
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTests {
    final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void logsStatusAtMatchingLevel(CapturedOutput out) throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/api/books"), new MockHttpServletResponse(),
                (req, res) -> ((HttpServletResponse) res).setStatus(404));
        assertTrue(out.getOut().contains("WARN"), out.getOut());
        assertTrue(out.getOut().contains("GET /api/books -> 404"), out.getOut());
    }

    @Test
    void logsExceptionAs500(CapturedOutput out) {
        assertThrows(IllegalStateException.class, () -> filter.doFilter(
                new MockHttpServletRequest("POST", "/api/boom"), new MockHttpServletResponse(),
                (req, res) -> { throw new IllegalStateException("boom"); }));
        assertTrue(out.getOut().contains("ERROR"), out.getOut());
        assertTrue(out.getOut().contains("POST /api/boom -> 500"), out.getOut());
    }
}
