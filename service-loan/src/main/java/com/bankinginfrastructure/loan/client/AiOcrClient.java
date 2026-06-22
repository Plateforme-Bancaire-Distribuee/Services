package com.bankinginfrastructure.loan.client;

import com.bankinginfrastructure.loan.dto.OcrSalaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(
        name = "ai-ocr-service",
        url = "${ai.service.url}"
)
public interface AiOcrClient {

    @PostMapping(value = "/api/v1/ai/extract-salary", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    OcrSalaryResponse extractSalary(@RequestPart("file") MultipartFile file);
}
