package com.creditx.posting.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.creditx.posting.dto.CommitTransactionRequest;
import com.creditx.posting.dto.TransactionAuthorizedEvent;
import com.creditx.posting.service.ProcessedEventService;
import com.creditx.posting.util.EventIdGenerator;
import com.creditx.posting.tracing.TransactionSpanTagger;

@ExtendWith(MockitoExtension.class)
class TransactionEventServiceImplTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ProcessedEventService processedEventService;

    @InjectMocks
    private TransactionEventServiceImpl transactionEventService;

    @Mock
    private TransactionSpanTagger transactionSpanTagger;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(transactionEventService, "creditMainServiceUrl", "http://localhost:8080");
    }

    @Test
    void shouldProcessTransactionAuthorizedEvent() {
        // given
        TransactionAuthorizedEvent event = createTransactionAuthorizedEvent(123L, 456L);
        String eventId = "transaction.authorized-123";
        String payloadHash = "hash123";

        try (MockedStatic<EventIdGenerator> mockedGenerator = Mockito.mockStatic(EventIdGenerator.class)) {
            mockedGenerator.when(() -> EventIdGenerator.generateEventId("transaction.authorized", 123L))
                    .thenReturn(eventId);
            mockedGenerator.when(() -> EventIdGenerator.generatePayloadHash(anyString()))
                    .thenReturn(payloadHash);

            when(processedEventService.isEventProcessed(eventId)).thenReturn(false);
            when(processedEventService.isPayloadProcessed(payloadHash)).thenReturn(false);
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("Success"));

            // when
            transactionEventService.processTransactionAuthorized(event);

            // then
            verify(transactionSpanTagger, times(1)).tagTransactionId(123L);
            verify(processedEventService, times(1)).isEventProcessed(eventId);
            verify(processedEventService, times(1)).isPayloadProcessed(payloadHash);
            verify(processedEventService, times(1)).markEventAsProcessed(eventId, payloadHash, "SUCCESS");

            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<HttpEntity<?>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate, times(1)).postForEntity(urlCaptor.capture(), entityCaptor.capture(), eq(String.class));

            assertThat(urlCaptor.getValue()).isEqualTo("http://localhost:8080/api/transactions/123/commit");
            
            @SuppressWarnings("unchecked")
            HttpEntity<CommitTransactionRequest> capturedEntity = (HttpEntity<CommitTransactionRequest>) entityCaptor.getValue();
            CommitTransactionRequest capturedRequest = capturedEntity.getBody();
            assertThat(capturedRequest).isNotNull();
            if (capturedRequest != null) {
                assertThat(capturedRequest.getTransactionId()).isEqualTo(123L);
                assertThat(capturedRequest.getHoldId()).isEqualTo(456L);
            }
        }
    }

    @Test
    void shouldSkipProcessingWhenEventAlreadyProcessed() {
        // given
        TransactionAuthorizedEvent event = createTransactionAuthorizedEvent(123L, 456L);
        String eventId = "transaction.authorized-123";

        try (MockedStatic<EventIdGenerator> mockedGenerator = Mockito.mockStatic(EventIdGenerator.class)) {
            mockedGenerator.when(() -> EventIdGenerator.generateEventId("transaction.authorized", 123L))
                    .thenReturn(eventId);

            when(processedEventService.isEventProcessed(eventId)).thenReturn(true);

            // when
            transactionEventService.processTransactionAuthorized(event);

            // then
            verify(transactionSpanTagger, times(1)).tagTransactionId(123L);
            verify(processedEventService, times(1)).isEventProcessed(eventId);
            verify(processedEventService, never()).isPayloadProcessed(anyString());
            verify(processedEventService, never()).markEventAsProcessed(anyString(), anyString(), anyString());
            verify(restTemplate, never()).postForEntity(anyString(), any(), any());
        }
    }

    @Test
    void shouldSkipProcessingWhenPayloadAlreadyProcessed() {
        // given
        TransactionAuthorizedEvent event = createTransactionAuthorizedEvent(123L, 456L);
        String eventId = "transaction.authorized-123";
        String payloadHash = "hash123";

        try (MockedStatic<EventIdGenerator> mockedGenerator = Mockito.mockStatic(EventIdGenerator.class)) {
            mockedGenerator.when(() -> EventIdGenerator.generateEventId("transaction.authorized", 123L))
                    .thenReturn(eventId);
            mockedGenerator.when(() -> EventIdGenerator.generatePayloadHash(anyString()))
                    .thenReturn(payloadHash);

            when(processedEventService.isEventProcessed(eventId)).thenReturn(false);
            when(processedEventService.isPayloadProcessed(payloadHash)).thenReturn(true);

            // when
            transactionEventService.processTransactionAuthorized(event);

            // then
            verify(transactionSpanTagger, times(1)).tagTransactionId(123L);
            verify(processedEventService, times(1)).isEventProcessed(eventId);
            verify(processedEventService, times(1)).isPayloadProcessed(payloadHash);
            verify(processedEventService, never()).markEventAsProcessed(anyString(), anyString(), anyString());
            verify(restTemplate, never()).postForEntity(anyString(), any(), any());
        }
    }

    @Test
    void shouldMarkAsFailedWhenRestTemplateFails() {
        // given
        TransactionAuthorizedEvent event = createTransactionAuthorizedEvent(123L, 456L);
        String eventId = "transaction.authorized-123";
        String payloadHash = "hash123";

        try (MockedStatic<EventIdGenerator> mockedGenerator = Mockito.mockStatic(EventIdGenerator.class)) {
            mockedGenerator.when(() -> EventIdGenerator.generateEventId("transaction.authorized", 123L))
                    .thenReturn(eventId);
            mockedGenerator.when(() -> EventIdGenerator.generatePayloadHash(anyString()))
                    .thenReturn(payloadHash);

            when(processedEventService.isEventProcessed(eventId)).thenReturn(false);
            when(processedEventService.isPayloadProcessed(payloadHash)).thenReturn(false);
            doThrow(new RuntimeException("API call failed"))
                    .when(restTemplate).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));

            // when & then
            assertThatThrownBy(() -> transactionEventService.processTransactionAuthorized(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Failed to send commit transaction request to CMS");

            verify(processedEventService, times(1)).markEventAsProcessed(eventId, "", "FAILED");
        }
    }

    @Test
    void shouldMarkAsFailedWhenPayloadHashGenerationFails() {
        // given
        TransactionAuthorizedEvent event = createTransactionAuthorizedEvent(123L, 456L);
        String eventId = "transaction.authorized-123";

        try (MockedStatic<EventIdGenerator> mockedGenerator = Mockito.mockStatic(EventIdGenerator.class)) {
            mockedGenerator.when(() -> EventIdGenerator.generateEventId("transaction.authorized", 123L))
                    .thenReturn(eventId);
            mockedGenerator.when(() -> EventIdGenerator.generatePayloadHash(anyString()))
                    .thenThrow(new RuntimeException("Hash generation failed"));

            when(processedEventService.isEventProcessed(eventId)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> transactionEventService.processTransactionAuthorized(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Hash generation failed");

            verify(transactionSpanTagger, times(1)).tagTransactionId(123L);
            verify(processedEventService, times(1)).isEventProcessed(eventId);
            verify(processedEventService, never()).isPayloadProcessed(anyString());
            verify(processedEventService, times(1)).markEventAsProcessed(eventId, "", "FAILED");
            verify(restTemplate, never()).postForEntity(anyString(), any(), any());
        }
    }

    private TransactionAuthorizedEvent createTransactionAuthorizedEvent(Long transactionId, Long holdId) {
        TransactionAuthorizedEvent event = new TransactionAuthorizedEvent();
        event.setTransactionId(transactionId);
        event.setHoldId(holdId);
        return event;
    }
}