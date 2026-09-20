package com.spendly.transactionservice.service;

import java.util.UUID;

public record CategoryMerge(UUID sourceId, UUID targetId, int transactionsMoved, int splitsMoved, int childrenMoved) {
}
