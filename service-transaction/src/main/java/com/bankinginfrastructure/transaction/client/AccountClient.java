package com.bankinginfrastructure.transaction.client;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "service-account",
        url = "${account.service.url}",
        path = "/api/v1/accounts"
)
public interface AccountClient {

    @PostMapping("/{id}/credit")
    Map<String, Object> credit(@PathVariable("id") Long id, @RequestBody Map<String, BigDecimal> body);

    @PostMapping("/{id}/debit")
    Map<String, Object> debit(@PathVariable("id") Long id, @RequestBody Map<String, BigDecimal> body);

    @GetMapping("/{id}")
    Map<String, Object> getAccount(@PathVariable("id") Long id);
}
