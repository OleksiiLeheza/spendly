package com.spendly.accountservice.api.mapper;

import com.spendly.accountservice.api.dto.AccountResponse;
import com.spendly.accountservice.api.dto.BalanceReconciliationResponse;
import com.spendly.accountservice.api.dto.OpenAccountRequest;
import com.spendly.accountservice.domain.Account;
import com.spendly.accountservice.service.BalanceReconciliation;
import com.spendly.accountservice.service.OpenAccount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AccountMapper {

    @Mapping(target = "currency", source = "balance.currency")
    AccountResponse toResponse(Account account);

    @Mapping(target = "currencyCode", source = "currency")
    OpenAccount toCommand(OpenAccountRequest request);

    @Mapping(target = "drift", expression = "java(reconciliation.drift())")
    @Mapping(target = "wasConsistent", expression = "java(reconciliation.wasConsistent())")
    BalanceReconciliationResponse toResponse(BalanceReconciliation reconciliation);
}
