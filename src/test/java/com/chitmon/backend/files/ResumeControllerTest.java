package com.chitmon.backend.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ResumeControllerTest {
    @TempDir Path directory;
    private Path pdf;
    private MockMvc mvc;
    private Logger logger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        pdf = directory.resolve("resume.pdf");
        mvc = MockMvcBuilders.standaloneSetup(new ResumeController(new ResumeService(pdf.toString())))
                .addFilters(new ResumeDownloadLoggingFilter()).build();
        logger = (Logger) LoggerFactory.getLogger(ResumeController.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detachLogs() {
        logger.detachAppender(logs);
        logs.stop();
    }

    @Test
    void servesPdfAsAnAttachmentWithoutCaching() throws Exception {
        byte[] content = "%PDF-1.7\nfirst version".getBytes(StandardCharsets.US_ASCII);
        Files.write(pdf, content);
        var response = mvc.perform(get("/files/resume.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(content))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Austin-Chitmon-Resume.pdf\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().longValue("Content-Length", content.length))
                .andReturn().getResponse();
        assertThat(UUID.fromString(response.getHeader("X-Request-Id"))).isNotNull();
        assertThat(logs.list).noneMatch(event -> event.getLevel().toString().equals("ERROR"));
    }

    @Test
    void replacementIsVisibleWithoutRestartingTheService() throws Exception {
        Files.writeString(pdf, "%PDF-1.7\nfirst version");
        mvc.perform(get("/files/resume.pdf")).andExpect(content().string("%PDF-1.7\nfirst version"));
        Path replacement = directory.resolve("resume.pdf.new");
        Files.writeString(replacement, "%PDF-1.7\nnew version");
        Files.move(replacement, pdf, StandardCopyOption.REPLACE_EXISTING);
        mvc.perform(get("/files/resume.pdf")).andExpect(content().string("%PDF-1.7\nnew version"));
    }

    @Test
    void missingFileReturns503WithACorrelatedErrorLogAndNoFilePathLeak() throws Exception {
        var response = mvc.perform(get("/files/resume.pdf"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.title").value("Resume unavailable"))
                .andReturn().getResponse();
        String requestId = response.getHeader("X-Request-Id");
        assertThat(response.getContentAsString()).contains(requestId).doesNotContain(directory.toString());
        assertThat(logs.list).anySatisfy(event -> {
            assertThat(event.getLevel().toString()).isEqualTo("ERROR");
            assertThat(event.getFormattedMessage()).contains(requestId, pdf.toString());
            assertThat(event.getThrowableProxy()).isNotNull();
        });
    }

    @Test
    void unreadableFilesAreLoggedAndReturn503() throws Exception {
        ResumeService service = mock(ResumeService.class);
        when(service.configuredFile()).thenReturn(pdf);
        when(service.readPdf()).thenThrow(new AccessDeniedException(pdf.toString()));
        var isolatedMvc = MockMvcBuilders.standaloneSetup(new ResumeController(service)).build();
        isolatedMvc.perform(get("/files/resume.pdf")).andExpect(status().isServiceUnavailable());
        assertThat(logs.list).anySatisfy(event ->
                assertThat(event.getThrowableProxy().getClassName()).contains("AccessDeniedException"));
    }

    @Test
    void invalidAndEmptyUploadsAreNotDownloaded() throws Exception {
        Files.writeString(pdf, "<html>Not a PDF</html>");
        mvc.perform(get("/files/resume.pdf")).andExpect(status().isServiceUnavailable());
        Files.write(pdf, new byte[0]);
        mvc.perform(get("/files/resume.pdf")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void oversizedDocumentsAreRejected() throws Exception {
        Files.write(pdf, new byte[ResumeService.MAX_PDF_BYTES + 1]);
        mvc.perform(get("/files/resume.pdf")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void headProvidesPdfMetadata() throws Exception {
        Files.writeString(pdf, "%PDF-1.7\nresume");
        mvc.perform(head("/files/resume.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().longValue("Content-Length", Files.size(pdf)));
    }

    @Test
    void otherFilenamesAreNotPublished() throws Exception {
        Files.writeString(directory.resolve("private.txt"), "not public");
        mvc.perform(get("/files/private.txt")).andExpect(status().isNotFound());
    }
}
