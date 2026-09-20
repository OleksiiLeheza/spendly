package com.spendly.transactionservice.api.mapper;

import com.spendly.transactionservice.api.dto.PostTransactionRequest;
import com.spendly.transactionservice.api.dto.SplitRequest;
import com.spendly.transactionservice.api.dto.SplitResponse;
import com.spendly.transactionservice.api.dto.TransactionFeedItem;
import com.spendly.transactionservice.api.dto.TransactionResponse;
import com.spendly.transactionservice.api.dto.UpdateTransactionRequest;
import com.spendly.transactionservice.domain.Split;
import com.spendly.transactionservice.domain.Transaction;
import com.spendly.transactionservice.domain.TransactionRow;
import com.spendly.transactionservice.service.PostTransaction;
import com.spendly.transactionservice.service.SplitCommand;
import com.spendly.transactionservice.service.UpdateTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TransactionMapper {

    @Mapping(target = "signedAmount", expression = "java(transaction.signedAmount())")
    TransactionResponse toResponse(Transaction transaction);

    SplitResponse toResponse(Split split);

    TransactionFeedItem toItem(TransactionRow row);

    PostTransaction toCommand(PostTransactionRequest request);

    UpdateTransaction toCommand(UpdateTransactionRequest request);

    SplitCommand toCommand(SplitRequest request);
}
