package com.chitmon.backend.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ResumeDownloadLoggingFilterTest {
    @Test
    void logsResponseWriteFailuresWithTheSameReferenceSentToTheBrowser() {
        var logger = (Logger) LoggerFactory.getLogger(ResumeDownloadLoggingFilter.class);
        var logs = new ListAppender<ILoggingEvent>();
        logs.start();
        logger.addAppender(logs);
        try {
            var request = new MockHttpServletRequest("GET", "/files/resume.pdf");
            var response = new MockHttpServletResponse();
            assertThrows(IOException.class, () -> new ResumeDownloadLoggingFilter().doFilter(request, response,
                    (req, res) -> { throw new IOException("Connection closed while sending PDF"); }));
            String requestId = response.getHeader("X-Request-Id");
            assertThat(requestId).isNotBlank();
            assertThat(request.getAttribute(ResumeDownloadLoggingFilter.REQUEST_ID_ATTRIBUTE)).isEqualTo(requestId);
            assertThat(logs.list).anySatisfy(event -> {
                assertThat(event.getFormattedMessage()).contains(requestId, "Resume response delivery failed");
                assertThat(event.getThrowableProxy().getMessage()).contains("Connection closed");
            });
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }

    @Test
    void doesNotChangeUnrelatedRequests() throws Exception {
        var request = new MockHttpServletRequest("GET", "/me");
        var response = new MockHttpServletResponse();
        new ResumeDownloadLoggingFilter().doFilter(request, response, (req, res) -> res.getWriter().write("unchanged"));
        assertThat(response.getContentAsString()).isEqualTo("unchanged");
        assertThat(response.getHeader("X-Request-Id")).isNull();
    }
}
