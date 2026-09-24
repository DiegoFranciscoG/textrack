package io.github.diegofranciscog.textrack.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.diegofranciscog.textrack.domain.Role;
import io.github.diegofranciscog.textrack.domain.ScanSource;
import io.github.diegofranciscog.textrack.dto.ReadingDtos.ScanRequest;
import io.github.diegofranciscog.textrack.repository.CutRepository;
import io.github.diegofranciscog.textrack.service.ReadingService;
import io.github.diegofranciscog.textrack.support.Fixtures;
import io.github.diegofranciscog.textrack.support.Fixtures.Scenario;
import io.github.diegofranciscog.textrack.support.IntegrationTest;
import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Tablero en vivo por WebSocket (STOMP)")
class DashboardWebSocketIT extends IntegrationTest {

    @LocalServerPort
    private int port;
    @Autowired
    private CutRepository cuts;
    @Autowired
    private ReadingService readings;

    private WebSocketStompClient client;

    @BeforeEach
    void setUp() {
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @AfterEach
    void tearDown() {
        client.stop();
    }

    private CompletableFuture<StompSession> connect(String authorization) {
        StompHeaders connectHeaders = new StompHeaders();
        if (authorization != null) {
            connectHeaders.add("Authorization", authorization);
        }
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() {
                });
    }

    @Test
    void connectionWithoutTokenIsRejected() {
        assertThatThrownBy(() -> connect(null).get(5, TimeUnit.SECONDS)).isInstanceOf(Exception.class);
    }

    @Test
    void acceptedReadingIsPushedToSubscribers() throws Exception {
        Scenario scenario = new Fixtures(mvc, bearer(Role.ADMIN), jdbc, cuts).scenario();
        StompSession session = connect(bearer(Role.VIEWER)).get(10, TimeUnit.SECONDS);
        BlockingQueue<Map<String, Object>> messages = new LinkedBlockingQueue<>();
        session.subscribe("/topic/dashboard", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                @SuppressWarnings("unchecked")
                Map<String, Object> snapshot = (Map<String, Object>) payload;
                messages.add(snapshot);
            }
        });
        Thread.sleep(300); // la suscripción es asíncrona

        readings.ingest(new ScanRequest(UUID.randomUUID(), Fixtures.payload(scenario.tickets().getFirst()),
                scenario.operatorCode(), OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), "tablet"), ScanSource.APP);

        Map<String, Object> snapshot = messages.poll(10, TimeUnit.SECONDS);
        assertThat(snapshot).isNotNull().containsKeys("lines", "plant", "recentReadings");
        assertThat(snapshot.get("recentReadings").toString()).contains(scenario.operatorCode());
        session.disconnect();
    }
}
