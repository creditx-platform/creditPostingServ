package com.creditx.posting.messaging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import com.creditx.posting.constants.EventTypes;
import com.creditx.posting.dto.TransactionAuthorizedEvent;
import com.creditx.posting.service.TransactionEventService;
import com.creditx.posting.util.EventValidationUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class TransactionEventListener {

    private final TransactionEventService transactionEventService;
    private final Tracer tracer;
    private final ObjectMapper objectMapper;

    @Bean
    public Consumer<Message<String>> transactionAuthorized() {
        return message -> {
            String payload = message.getPayload();
            String traceId = (String) message.getHeaders().get("X-Trace-Id");
            
            // Validate event type before processing
            if (!EventValidationUtils.validateEventType(message, EventTypes.TRANSACTION_AUTHORIZED)) {
                log.warn("Skipping message with invalid event type. Expected: {}, Headers: {}, Payload: {}", 
                        EventTypes.TRANSACTION_AUTHORIZED, message.getHeaders(), payload);
                return;
            }
            
            Span span = tracer.nextSpan()
                    .name("transaction-authorized-listener")
                    .tag("service", "creditPostingServ")
                    .tag("event.type", EventTypes.TRANSACTION_AUTHORIZED);
                    
            if (traceId != null) {
                span.tag("trace.parent.id", traceId);
            }
            
            try {
                span.start();
                log.info("Received transaction.authorized event: {}", payload);
                TransactionAuthorizedEvent event = objectMapper.readValue(payload, TransactionAuthorizedEvent.class);
                
                // Validate that the event has a holdId - skip events without holdId
                if (event.getHoldId() == null) {
                    log.warn("Skipping transaction.authorized event without holdId for transaction: {} - payload: {}", 
                            event.getTransactionId(), payload);
                    return;
                }
                
                transactionEventService.processTransactionAuthorized(event);
                log.info("Successfully processed transaction.authorized for transaction: {}", event.getTransactionId());
            } catch (Exception e) {
                span.tag("error", e.getMessage());
                log.error("Failed to process transaction.authorized event: {}", payload, e);
                throw new RuntimeException("Failed to process transaction.authorized event", e);
            } finally {
                span.end();
            }
        };
    }
}
