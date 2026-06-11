package com.banking.customer_service.config;

import com.banking.customer_service.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDate;

@FeignClient(
        name = "document-service",
        url = "${services.document-service.url}",
        configuration = FeignConfig.class
)
public interface DocumentServiceClient {

    record OcrTriggerRequest(
            String clientNom,
            String clientPrenom,
            LocalDate clientDateNaissance
    ) {}

    record OcrResponse(
            Long documentId,
            boolean coherent,
            String commentaire,
            double confiance
    ) {}

    @PostMapping("/api/v1/documents/{documentId}/ocr")
    OcrResponse triggerOcr(
            @PathVariable("documentId") Long documentId,
            @RequestBody OcrTriggerRequest request
    );
}