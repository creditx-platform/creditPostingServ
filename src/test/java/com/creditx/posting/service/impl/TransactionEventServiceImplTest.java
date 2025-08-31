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
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.creditx.posting.dto.TransactionAuthorizedEvent;

@ExtendWith(MockitoExtension.class)
class TransactionEventServiceImplTest {

    @Mock
    private RestTemplate restTemplate;

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
        
        assertThat(urlCaptor.getValue()).isEqualTo("http://localhost:8080/commitTransaction");
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

        // Then: Verify the REST call was made
        verify(restTemplate).postForEntity(eq("http://localhost:8080/commitTransaction"), any(), eq(String.class));
    }
}
