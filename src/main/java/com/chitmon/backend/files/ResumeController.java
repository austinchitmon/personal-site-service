package com.chitmon.backend.files;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResumeController {
    private static final Logger log = LoggerFactory.getLogger(ResumeController.class);
    private final ResumeService resumes;

    public ResumeController(ResumeService resumes) {
        this.resumes = resumes;
    }

    @GetMapping("/files/resume.pdf")
    public ResponseEntity<?> downloadResume(HttpServletRequest request) {
        Object suppliedId = request.getAttribute(ResumeDownloadLoggingFilter.REQUEST_ID_ATTRIBUTE);
        String requestId = suppliedId instanceof String id ? id : UUID.randomUUID().toString();
        try {
            byte[] pdf = resumes.readPdf();
            log.debug("Resume download ready requestId={} bytes={}", requestId, pdf.length);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(pdf.length)
                    .cacheControl(CacheControl.noStore())
                    .header("X-Request-Id", requestId)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename("Austin-Chitmon-Resume.pdf").build().toString())
                    .body(pdf);
        } catch (IOException ex) {
            log.error("Resume download failed requestId={} file={}", requestId, resumes.configuredFile(), ex);
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                    "The resume is temporarily unavailable. Please try again later.");
            problem.setTitle("Resume unavailable");
            problem.setProperty("requestId", requestId);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .cacheControl(CacheControl.noStore())
                    .header("X-Request-Id", requestId)
                    .body(problem);
        }
    }
}
