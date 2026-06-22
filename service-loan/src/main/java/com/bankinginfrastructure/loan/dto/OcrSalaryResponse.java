package com.bankinginfrastructure.loan.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OcrSalaryResponse {

    private BigDecimal salary;
    private Double confidence;
}
