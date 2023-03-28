package com.unicorn.store.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unicorn.store.exceptions.PublisherException;
import com.unicorn.store.model.Unicorn;
import com.unicorn.store.model.UnicornEventType;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.eventbridge.EventBridgeAsyncClient;
import software.amazon.awssdk.services.eventbridge.model.EventBridgeException;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

import java.util.concurrent.ExecutionException;

@Service
public class UnicornPublisher {

    private final ObjectMapper objectMapper;
    private static final EventBridgeAsyncClient eventBridgeClient = EventBridgeAsyncClient
            .builder()
            .credentialsProvider(DefaultCredentialsProvider.create())
            //.credentialsProvider(EnvironmentVariableCredentialsProvider.create())
            //.region(Region.of(System.getenv(SdkSystemSetting.AWS_REGION.environmentVariable())))
            .build();

    public UnicornPublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void publish(Unicorn unicorn, UnicornEventType unicornEventType) {
        try {
            var unicornJson = objectMapper.writeValueAsString(unicorn);
            var eventsRequest = createEventRequestEntry(unicornEventType, unicornJson);

            eventBridgeClient.putEvents(eventsRequest).get();
            System.out.println("Publishing ...");
            System.out.println(unicornJson);
        } catch (JsonProcessingException e) {
            System.out.println("Error JsonProcessingException ...");
            System.out.println(e.getMessage());
            System.out.println(e.getStackTrace());
            // throw new PublisherException("Error while serializing the Unicorn", e);
        } catch (EventBridgeException | ExecutionException | InterruptedException e) {
            System.out.println("Error EventBridgeException | ExecutionException ...");
            System.out.println(e.getMessage());
            System.out.println(e.getStackTrace());
            // throw new PublisherException("Error while publishing the event", e);
        }
    }

    private PutEventsRequest createEventRequestEntry(UnicornEventType unicornEventType, String unicornJson) {
        var entry = PutEventsRequestEntry.builder()
                .source("com.unicorn.store")
                .eventBusName("unicorns")
                .detailType(unicornEventType.name())
                .detail(unicornJson)
                .build();

        return PutEventsRequest.builder()
                .entries(entry)
                .build();
    }
}
