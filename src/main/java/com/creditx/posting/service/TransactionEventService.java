package com.creditx.posting.service;

import com.creditx.posting.dto.TransactionAuthorizedEvent;

public interface TransactionEventService {
    void processTransactionAuthorized(TransactionAuthorizedEvent event);
}
