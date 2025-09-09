package com.creditx.posting.messaging;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.creditx.posting.constants.EventTypes;
import com.creditx.posting.dto.TransactionAuthorizedEvent;
import com.creditx.posting.service.TransactionEventService;
import com.creditx.posting.tracing.TransactionSpanTagger;
import com.creditx.posting.util.EventValidationUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

@ExtendWith(MockitoExtension.class)
class TransactionEventListenerTest {

  @Mock
  private TransactionEventService transactionEventService;

  @Mock
  private ObjectMapper objectMapper;

  @Mock
  private TransactionSpanTagger transactionSpanTagger;

  @InjectMocks
  private TransactionEventListener transactionEventListener;

  private Consumer<Message<String>> transactionAuthorizedConsumer;

  @BeforeEach
  void setup() {
    transactionAuthorizedConsumer = transactionEventListener.transactionAuthorized();
  }

  @Test
  void shouldProcessValidTransactionAuthorizedEvent() throws Exception {
    // given
    String payload = "{\"transactionId\":123,\"holdId\":456}";

    Message<String> message = MessageBuilder.withPayload(payload)
        .setHeader("eventType", EventTypes.TRANSACTION_AUTHORIZED).build();

    TransactionAuthorizedEvent event = new TransactionAuthorizedEvent();
    event.setTransactionId(123L);
    event.setHoldId(456L);

    try (MockedStatic<EventValidationUtils> mockedUtils = Mockito.mockStatic(
        EventValidationUtils.class)) {
      mockedUtils.when(
              () -> EventValidationUtils.validateEventType(message, EventTypes.TRANSACTION_AUTHORIZED))
          .thenReturn(true);

      when(objectMapper.readValue(payload, TransactionAuthorizedEvent.class)).thenReturn(event);

      // when
      transactionAuthorizedConsumer.accept(message);

      // then
      verify(transactionEventService, times(1)).processTransactionAuthorized(event);
      verify(transactionSpanTagger, times(1)).tagTransactionId(123L);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {EventTypes.HOLD_CREATED, EventTypes.HOLD_EXPIRED, EventTypes.HOLD_VOIDED,
      EventTypes.TRANSACTION_FAILED, EventTypes.TRANSACTION_INITIATED,
      EventTypes.TRANSACTION_POSTED})
  void shouldNotProcessInvalidTransactionAuthorizedEvent(String eventType) throws Exception {
    // given
    String payload = "{\"transactionId\":123,\"holdId\":456}";

    Message<String> message = MessageBuilder.withPayload(payload).setHeader("eventType", eventType)
        .build();

    TransactionAuthorizedEvent event = new TransactionAuthorizedEvent();
    event.setTransactionId(123L);
    event.setHoldId(456L);

    try (MockedStatic<EventValidationUtils> mockedUtils = Mockito.mockStatic(
        EventValidationUtils.class)) {
      mockedUtils.when(
              () -> EventValidationUtils.validateEventType(message, EventTypes.TRANSACTION_AUTHORIZED))
          .thenReturn(false);

      // when
      transactionAuthorizedConsumer.accept(message);

      // then
      verify(transactionEventService, never()).processTransactionAuthorized(event);
      // Tagger shouldn't be called because validation failed
      verify(transactionSpanTagger, never()).tagTransactionId(123L);
    }
  }
}