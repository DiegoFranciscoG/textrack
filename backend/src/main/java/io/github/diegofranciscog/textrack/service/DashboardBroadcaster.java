package io.github.diegofranciscog.textrack.service;

import io.github.diegofranciscog.textrack.config.WebSocketConfig;
import io.github.diegofranciscog.textrack.service.ReadingService.ReadingAcceptedEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publica el tablero por WebSocket. Las lecturas marcan el tablero como «sucio» y se envía como máximo cada
 * 2 s (agrupa ráfagas de sincronización); cada 30 s se reenvía igual para actualizar minutos y paros en curso.
 */
@Component
public class DashboardBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(DashboardBroadcaster.class);

    private final DashboardService dashboard;
    private final SimpMessagingTemplate messaging;
    private final SimpUserRegistry users;
    private final PlantTime time;
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public DashboardBroadcaster(DashboardService dashboard, SimpMessagingTemplate messaging, SimpUserRegistry users,
                                PlantTime time) {
        this.dashboard = dashboard;
        this.messaging = messaging;
        this.users = users;
        this.time = time;
    }

    @EventListener
    public void onReading(ReadingAcceptedEvent event) {
        if (event.workDate().equals(time.today())) {
            dirty.set(true);
        }
    }

    @Scheduled(fixedDelay = 2_000)
    public void flushChanges() {
        if (dirty.getAndSet(false)) {
            publish();
        }
    }

    @Scheduled(fixedRate = 30_000, initialDelay = 30_000)
    public void heartbeat() {
        publish();
    }

    private void publish() {
        if (users.getUserCount() == 0) {
            return;
        }
        try {
            messaging.convertAndSend(WebSocketConfig.DASHBOARD_TOPIC, dashboard.snapshot(time.today()));
        } catch (RuntimeException e) {
            log.warn("No se pudo publicar el tablero: {}", e.getMessage());
        }
    }
}
