package com.creditx.posting.messaging;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.creditx.posting.dto.TransactionAuthorizedEvent;
import com.creditx.posting.service.TransactionEventService;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class TransactionEventListenerIntegrationTest {

    @Mock
    private TransactionEventService transactionEventService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TransactionEventListener transactionEventListener;

    @Test
    void transactionAuthorized_consumerProcessesEventSuccessfully() throws Exception {
        // Given: Valid transaction authorized event
        TransactionAuthorizedEvent event = TransactionAuthorizedEvent.builder()
                .transactionId(999L)
                .holdId(12345L)
                .issuerAccountId(1L)
                .merchantAccountId(2L)
                .amount(new BigDecimal("250.00"))
                .currency("USD")
                .status("AUTHORIZED")
                .build();

        String payload = "{\"transactionId\":999,\"holdId\":12345,\"issuerAccountId\":1,\"merchantAccountId\":2,\"amount\":250.00,\"currency\":\"USD\",\"status\":\"AUTHORIZED\"}";

        // Mock ObjectMapper to return the event
        given(objectMapper.readValue(payload, TransactionAuthorizedEvent.class)).willReturn(event);

        // When: Event is consumed
        transactionEventListener.transactionAuthorized().accept(payload);

        // Then: Verify service was called with the correct event
        verify(objectMapper).readValue(payload, TransactionAuthorizedEvent.class);
        verify(transactionEventService).processTransactionAuthorized(event);
    }
}
