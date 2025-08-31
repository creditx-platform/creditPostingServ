package com.creditx.posting.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.creditx.posting.dto.CommitTransactionRequest;
import com.creditx.posting.dto.TransactionAuthorizedEvent;
import com.creditx.posting.service.ProcessedEventService;
import com.creditx.posting.service.TransactionEventService;
import com.creditx.posting.util.EventIdGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionEventServiceImpl implements TransactionEventService {

    private final RestTemplate restTemplate;
    private final ProcessedEventService processedEventService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.creditmain.url:http://localhost:8080}")
    private String creditMainServiceUrl;

    @Override
    @Transactional
    public void processTransactionAuthorized(TransactionAuthorizedEvent event) {
        // Generate unique event ID for deduplication
        String eventId = EventIdGenerator.generateEventId("transaction.authorized", event.getTransactionId());
        
        // Check if event has already been processed
        if (processedEventService.isEventProcessed(eventId)) {
            log.info("Event {} has already been processed, skipping", eventId);
            return;
        }
        
        try {
            // Generate payload hash for additional deduplication
            String payload = objectMapper.writeValueAsString(event);
            String payloadHash = EventIdGenerator.generatePayloadHash(payload);
            
            // Check if payload has already been processed
            if (processedEventService.isPayloadProcessed(payloadHash)) {
                log.info("Payload with hash {} has already been processed, skipping", payloadHash);
                return;
            }
            
            log.info("Processing transaction.authorized event for transaction: {}", event.getTransactionId());
            
            // Prepare to settle by calling CMS /commitTransaction
            CommitTransactionRequest commitRequest = CommitTransactionRequest.builder()
                    .transactionId(event.getTransactionId())
                    .holdId(event.getHoldId())
                    .build();

            sendCommitTransactionRequest(commitRequest);
            
            // Mark event as processed
            processedEventService.markEventAsProcessed(eventId, payloadHash, "SUCCESS");
            log.info("Successfully processed transaction.authorized for transaction: {}", event.getTransactionId());
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event payload for transaction: {}", event.getTransactionId(), e);
            processedEventService.markEventAsProcessed(eventId, "", "FAILED");
            throw new RuntimeException("Failed to serialize event payload", e);
        } catch (Exception e) {
            log.error("Failed to process transaction.authorized event for transaction: {}", event.getTransactionId(), e);
            // Mark event as processed with failed status
            processedEventService.markEventAsProcessed(eventId, "", "FAILED");
            throw e;
        }
    }

    private void sendCommitTransactionRequest(CommitTransactionRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CommitTransactionRequest> entity = new HttpEntity<>(request, headers);

        try {
            log.info("Sending commit transaction request to CMS for transaction: {}", request.getTransactionId());
            String url = creditMainServiceUrl + "/transactions/" + request.getTransactionId() + "/commit";
            restTemplate.postForEntity(url, entity, String.class);
            log.info("Successfully sent commit transaction request for transaction: {}", request.getTransactionId());
        } catch (Exception e) {
            log.error("Failed to send commit transaction request for transaction: {}", request.getTransactionId(), e);
            throw new RuntimeException("Failed to send commit transaction request to CMS", e);
        }
    }
}
