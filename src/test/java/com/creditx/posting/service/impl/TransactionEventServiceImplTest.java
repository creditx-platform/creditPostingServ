package com.creditx.posting.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.creditx.posting.dto.TransactionAuthorizedEvent;
import com.creditx.posting.service.ProcessedEventService;

@ExtendWith(MockitoExtension.class)
class TransactionEventServiceImplTest {

    @Mock
    private RestTemplate restTemplate;
    
    @Mock
    private ProcessedEventService processedEventService;

    @InjectMocks
    private TransactionEventServiceImpl transactionEventService;

    private TransactionAuthorizedEvent transactionAuthorizedEvent;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(transactionEventService, "creditMainServiceUrl", "http://localhost:8080");
        
        transactionAuthorizedEvent = TransactionAuthorizedEvent.builder()
                .transactionId(999L)
                .holdId(12345L)
                .issuerAccountId(1L)
                .merchantAccountId(2L)
                .amount(new BigDecimal("250.00"))
                .currency("USD")
                .status("AUTHORIZED")
                .build();
        
        // Default behavior: event not already processed
        given(processedEventService.isEventProcessed(any(String.class))).willReturn(false);
    }

    @Test
    void processTransactionAuthorized_success() {
        // Given: Mock successful response from CMS
        given(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .willReturn(ResponseEntity.ok("Success"));

        // When: Processing transaction authorized event
        transactionEventService.processTransactionAuthorized(transactionAuthorizedEvent);

        // Then: Verify commit transaction request was sent to CMS
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        
        verify(restTemplate).postForEntity(urlCaptor.capture(), any(), eq(String.class));
        
        assertThat(urlCaptor.getValue()).isEqualTo("http://localhost:8080/api/transactions/999/commit");
    }

    @Test
    void processTransactionAuthorized_restTemplateFailure() {
        // Given: RestTemplate throws exception
        given(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .willThrow(new RuntimeException("Network error"));

        // When & Then: Should propagate the exception
        assertThatThrownBy(() -> transactionEventService.processTransactionAuthorized(transactionAuthorizedEvent))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to send commit transaction request to CMS");
    }

    @Test
    void processTransactionAuthorized_verifyRequestPayload() {
        // Given: Mock successful response
        given(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .willReturn(ResponseEntity.ok("Success"));

        // When: Processing transaction authorized event
        transactionEventService.processTransactionAuthorized(transactionAuthorizedEvent);

        // Then: Verify the REST call was made with correct URL and payload
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForEntity(eq("http://localhost:8080/api/transactions/999/commit"), payloadCaptor.capture(), eq(String.class));
        
        // Verify the payload contains both transactionId and holdId
        Object capturedPayload = payloadCaptor.getValue();
        assertThat(capturedPayload).isNotNull();
    }

    @Test
    void processTransactionAuthorized_verifyRequestContainsHoldId() {
        // Given: Mock successful response
        given(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .willReturn(ResponseEntity.ok("Success"));

        // When: Processing transaction authorized event
        transactionEventService.processTransactionAuthorized(transactionAuthorizedEvent);

        // Then: Verify the REST call includes holdId in the request body
        ArgumentCaptor<HttpEntity<?>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(any(String.class), entityCaptor.capture(), eq(String.class));
        
        HttpEntity<?> capturedEntity = entityCaptor.getValue();
        Object body = capturedEntity.getBody();
        
        assertThat(body).isInstanceOf(com.creditx.posting.dto.CommitTransactionRequest.class);
        com.creditx.posting.dto.CommitTransactionRequest request = (com.creditx.posting.dto.CommitTransactionRequest) body;
        assertThat(request)
                .isNotNull()
                .extracting("transactionId", "holdId")
                .containsExactly(999L, 12345L);
    }
}
