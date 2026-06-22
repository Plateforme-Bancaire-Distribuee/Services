package com.banking.customer_service.feignClients;

import com.banking.customer_service.dto.request.AccountRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "service-account", url = "${services.account-service.url}")
public interface AccountClient {

    @GetMapping("/accounts/client/{clientId}")
    List<AccountRequest> getAccountsByClientId(@PathVariable("clientId") Long clientId);
}
