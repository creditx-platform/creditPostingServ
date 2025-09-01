package com.creditx.posting.messaging;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

import com.creditx.posting.dto.TransactionAuthorizedEvent;
import com.creditx.posting.service.TransactionEventService;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

@ExtendWith(MockitoExtension.class)
class TransactionEventListenerIntegrationTest {

    @Mock
    private TransactionEventService transactionEventService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

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

        // Create message with headers
        Map<String, Object> headers = new HashMap<>();
        headers.put("X-Trace-Id", "test-trace-id");
        headers.put("X-Span-Id", "test-span-id");
        headers.put("eventType", "transaction.authorized");
        Message<String> message = new GenericMessage<>(payload, new MessageHeaders(headers));

        // Mock Tracer and Span
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name("transaction-authorized-listener")).thenReturn(span);
        when(span.tag("service", "creditPostingServ")).thenReturn(span);
        when(span.tag("event.type", "transaction.authorized")).thenReturn(span);
        when(span.tag("trace.parent.id", "test-trace-id")).thenReturn(span);

        // Mock ObjectMapper to return the event
        given(objectMapper.readValue(payload, TransactionAuthorizedEvent.class)).willReturn(event);

        // When: Event is consumed
        transactionEventListener.transactionAuthorized().accept(message);

        // Then: Verify service was called with the correct event
        verify(objectMapper).readValue(payload, TransactionAuthorizedEvent.class);
        verify(transactionEventService).processTransactionAuthorized(event);
        verify(span).start();
        verify(span).end();
    }
}
