package com.creditx.posting.messaging;

import com.creditx.posting.constants.EventTypes;
import com.creditx.posting.dto.TransactionAuthorizedEvent;
import com.creditx.posting.service.TransactionEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionEventListenerEventTypeValidationTest {

    @Mock
    private TransactionEventService transactionEventService;

    @Mock
    private Tracer tracer;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Span span;

    private TransactionEventListener transactionEventListener;

    @BeforeEach
    void setUp() {
        transactionEventListener = new TransactionEventListener(transactionEventService, tracer, objectMapper);
        
        // Setup default span behavior
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.tag(anyString(), anyString())).thenReturn(span);
    }

    @Test
    void transactionAuthorized_shouldSkipProcessingWhenEventTypeHeaderMissing() throws Exception {
        // Given: Valid transaction authorized event without event type header
        String payload = "{\"transactionId\":999,\"holdId\":12345,\"issuerAccountId\":1,\"merchantAccountId\":2,\"amount\":250.00,\"currency\":\"USD\",\"status\":\"AUTHORIZED\"}";

        // Create message without event type header
        Map<String, Object> headers = new HashMap<>();
        headers.put("X-Trace-Id", "test-trace-id");
        Message<String> message = new GenericMessage<>(payload, new MessageHeaders(headers));

        // When: Event is consumed
        transactionEventListener.transactionAuthorized().accept(message);

        // Then: Service should not be called and span should not be created
        verify(transactionEventService, never()).processTransactionAuthorized(any());
        verify(tracer, never()).nextSpan();
        verify(objectMapper, never()).readValue(anyString(), eq(TransactionAuthorizedEvent.class));
    }

    @Test
    void transactionAuthorized_shouldSkipProcessingWhenEventTypeMismatch() throws Exception {
        // Given: Valid transaction authorized event with wrong event type
        String payload = "{\"transactionId\":999,\"holdId\":12345,\"issuerAccountId\":1,\"merchantAccountId\":2,\"amount\":250.00,\"currency\":\"USD\",\"status\":\"AUTHORIZED\"}";

        // Create message with wrong event type header
        Map<String, Object> headers = new HashMap<>();
        headers.put("X-Trace-Id", "test-trace-id");
        headers.put(EventTypes.EVENT_TYPE_HEADER, "hold.created"); // Wrong event type
        Message<String> message = new GenericMessage<>(payload, new MessageHeaders(headers));

        // When: Event is consumed
        transactionEventListener.transactionAuthorized().accept(message);

        // Then: Service should not be called and span should not be created
        verify(transactionEventService, never()).processTransactionAuthorized(any());
        verify(tracer, never()).nextSpan();
        verify(objectMapper, never()).readValue(anyString(), eq(TransactionAuthorizedEvent.class));
    }

    @Test
    void transactionAuthorized_shouldProcessWhenEventTypeMatches() throws Exception {
        // Given: Valid transaction authorized event with correct event type
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

        // Create message with correct event type header
        Map<String, Object> headers = new HashMap<>();
        headers.put("X-Trace-Id", "test-trace-id");
        headers.put(EventTypes.EVENT_TYPE_HEADER, EventTypes.TRANSACTION_AUTHORIZED);
        Message<String> message = new GenericMessage<>(payload, new MessageHeaders(headers));

        // Mock ObjectMapper to return the event
        given(objectMapper.readValue(payload, TransactionAuthorizedEvent.class)).willReturn(event);

        // When: Event is consumed
        transactionEventListener.transactionAuthorized().accept(message);

        // Then: Service should be called
        verify(objectMapper).readValue(payload, TransactionAuthorizedEvent.class);
        verify(transactionEventService).processTransactionAuthorized(event);
        verify(span).start();
        verify(span).end();
    }
}
