package com.chitmon.backend.files;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chitmon.backend.config.CorsConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class ResumeCorsTest {
    @TempDir Path directory;

    @Configuration
    @EnableWebMvc
    static class WebConfiguration { }

    private AnnotationConfigWebApplicationContext context(String profile) throws Exception {
        Path pdf = directory.resolve("resume.pdf");
        Files.writeString(pdf, "%PDF-1.7\nresume");
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().setActiveProfiles(profile);
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("resume-test", Map.of("resume.file-path", pdf.toString())));
        context.register(WebConfiguration.class, ResumeController.class, ResumeService.class,
                CorsConfig.LocalCorsConfig.class, CorsConfig.ProdCorsConfig.class);
        context.refresh();
        return context;
    }

    @Test
    void localFrontendCanGetAndInspectTheResume() throws Exception {
        try (var context = context("local")) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            for (String origin : new String[] {"http://localhost:4200", "http://127.0.0.1:4200"}) {
                mvc.perform(get("/files/resume.pdf").header("Origin", origin))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Access-Control-Allow-Origin", origin))
                        .andExpect(header().string("Access-Control-Expose-Headers", "X-Request-Id"));
                mvc.perform(head("/files/resume.pdf").header("Origin", origin)).andExpect(status().isOk());
            }
        }
    }

    @Test
    void productionExposesTheReferenceHeaderToTheWebsite() throws Exception {
        try (var context = context("production")) {
            MockMvcBuilders.webAppContextSetup(context).build()
                    .perform(head("/files/resume.pdf").header("Origin", "https://chitmon.com"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "https://chitmon.com"))
                    .andExpect(header().string("Access-Control-Expose-Headers", "X-Request-Id"));
        }
    }

    @Test
    void productionDoesNotAllowLocalOrUnrelatedOrigins() throws Exception {
        try (var context = context("production")) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            for (String origin : new String[] {"http://localhost:4200", "https://example.org"}) {
                mvc.perform(get("/files/resume.pdf").header("Origin", origin))
                        .andExpect(status().isForbidden())
                        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
            }
        }
    }
}
