package com.chitmon.backend.shipmentTracker.carrier;

import java.time.Duration;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Scrapes USPS's public tracking lookup page (no official API/key). The URL
 * and CSS selectors below are a best-effort starting point written without
 * access to the live page — they MUST be verified/corrected against a real
 * response (a few real tracking numbers in different states: pre-shipment,
 * in-transit, delivered) before this is trusted in production. Any parse
 * failure degrades to {@link TrackingResult#unavailable()} rather than
 * throwing, so a markup change breaks status freshness, not the API.
 */
@Component
public class UspsTrackingClient implements CarrierTrackingClient {

    private static final Logger log = LoggerFactory.getLogger(UspsTrackingClient.class);
    private static final String CODE = "USPS";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private final WebClient uspsWebClient;
    private final String trackingUrlTemplate;

    public UspsTrackingClient(
            @Qualifier("uspsWebClient") WebClient uspsWebClient,
            @Value("${shipment-tracker.usps.tracking-url}") String trackingUrlTemplate) {
        this.uspsWebClient = uspsWebClient;
        this.trackingUrlTemplate = trackingUrlTemplate;
    }

    @Override
    public String supportedCarrierCode() {
        return CODE;
    }

    @Override
    public TrackingResult fetchStatus(String trackingNumber) {
        try {
            String html = uspsWebClient.get()
                    .uri(trackingUrlTemplate, trackingNumber)
                    .header(HttpHeaders.USER_AGENT, USER_AGENT)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));

            if (html == null) {
                return TrackingResult.unavailable();
            }
            return parse(html);
        } catch (Exception e) {
            log.warn("USPS tracking fetch/parse failed for {}: {}", trackingNumber, e.getMessage());
            return TrackingResult.unavailable();
        }
    }

    // TODO: verify these selectors against a live response before relying on this.
    private TrackingResult parse(String html) {
        Document doc = Jsoup.parse(html);

        Element statusEl = doc.selectFirst(".tb-status, .delivery_status, [data-testid=tracking-status]");
        if (statusEl == null) {
            return TrackingResult.unavailable();
        }
        String statusText = statusEl.text().trim();

        Element locationEl = doc.selectFirst(".tb-location, .location, [data-testid=tracking-location]");
        String lastLocation = locationEl != null ? locationEl.text().trim() : null;

        boolean delivered = statusText.toLowerCase().contains("delivered");

        return TrackingResult.of(statusText, lastLocation, null, delivered);
    }
}
