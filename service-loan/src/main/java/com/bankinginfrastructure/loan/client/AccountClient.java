package com.bankinginfrastructure.loan.client;

import java.util.List;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "service-account",
        url = "${account.service.url}",
        path = "/api/v1/accounts"
)
public interface AccountClient {

    @GetMapping("/client/{clientId}")
    List<Map<String, Object>> getAccountsByClientId(@PathVariable("clientId") Long clientId);
}
