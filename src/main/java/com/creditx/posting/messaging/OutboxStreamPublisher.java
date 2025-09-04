package com.creditx.posting.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@Service
@Validated
@RequiredArgsConstructor
public class OutboxStreamPublisher {

    private final StreamBridge streamBridge;

    @Value("${app.outbox.binding}")
    private String bindingName;

    public void publish(@NotNull String key, @NotNull String payload) {

        Message<String> message = MessageBuilder
                .withPayload(payload)
                .setHeader("key", key)
                .build();

        streamBridge.send(bindingName, message);
    }
}
