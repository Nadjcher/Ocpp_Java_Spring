package com.evse.simulator.performance;

import com.evse.simulator.performance.model.ConnectionResult;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Pool de connexions WebSocket haute performance.
 * Optimise pour 25K+ connexions simultanees.
 */
@Slf4j
public class ConnectionPool {

    private final String baseUrl;
    private final String cpIdPrefix;
    private final int targetConnections;
    private final RateLimiter rateLimiter;

    private final ConcurrentHashMap<String, PerfWebSocketClient> connections = new ConcurrentHashMap<>();
    private final AtomicInteger activeCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);

    private final ExecutorService connectionExecutor;
    private final ScheduledExecutorService scheduler;

    private Consumer<ConnectionResult> connectionCallback;
    private Consumer<MessageEvent> messageCallback;

    private volatile boolean running = false;

    public ConnectionPool(String baseUrl, String cpIdPrefix, int targetConnections, double connectionsPerSecond) {
        this.baseUrl = baseUrl;
        this.cpIdPrefix = cpIdPrefix;
        this.targetConnections = targetConnections;
        this.rateLimiter = RateLimiter.create(connectionsPerSecond);

        // ThreadPool optimise pour I/O bound
        int poolSize = Math.min(targetConnections, Runtime.getRuntime().availableProcessors() * 50);
        this.connectionExecutor = new ThreadPoolExecutor(
                poolSize / 2,
                poolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(targetConnections * 2),
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(0);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "perf-conn-" + counter.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        this.scheduler = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "perf-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    public void setConnectionCallback(Consumer<ConnectionResult> callback) {
        this.connectionCallback = callback;
    }

    public void setMessageCallback(Consumer<MessageEvent> callback) {
        this.messageCallback = callback;
    }

    /**
     * Demarre la creation des connexions avec rate limiting.
     */
    public CompletableFuture<Void> startConnections() {
        running = true;
        log.info("Demarrage pool connexions: target={}, rate={}/s",
                targetConnections, rateLimiter.getRate());

        return CompletableFuture.runAsync(() -> {
            for (int i = 0; i < targetConnections && running; i++) {
                rateLimiter.acquire();
                if (!running) break;

                final int index = i;
                connectionExecutor.submit(() -> createConnection(index));
            }
        }, connectionExecutor);
    }

    private void createConnection(int index) {
        String cpId = String.format("%s-%06d", cpIdPrefix, index);
        String wsUrl = baseUrl + "/" + cpId;

        long startTime = System.nanoTime();

        try {
            URI uri = new URI(wsUrl);
            PerfWebSocketClient client = new PerfWebSocketClient(uri, cpId, startTime);

            boolean connected = client.connectBlocking(10, TimeUnit.SECONDS);
            long connectLatency = (System.nanoTime() - startTime) / 1_000_000;

            if (connected) {
                connections.put(cpId, client);
                activeCount.incrementAndGet();
                successCount.incrementAndGet();

                if (connectionCallback != null) {
                    connectionCallback.accept(ConnectionResult.success(cpId, connectLatency, 0));
                }

                log.debug("Connexion reussie: {} ({}ms)", cpId, connectLatency);
            } else {
                failedCount.incrementAndGet();
                if (connectionCallback != null) {
                    connectionCallback.accept(ConnectionResult.failed(cpId, "Connection timeout"));
                }
            }
        } catch (Exception e) {
            failedCount.incrementAndGet();
            if (connectionCallback != null) {
                connectionCallback.accept(ConnectionResult.failed(cpId, e.getMessage()));
            }
            log.debug("Echec connexion {}: {}", cpId, e.getMessage());
        }
    }

    /**
     * Envoie un message a toutes les connexions actives.
     */
    public CompletableFuture<Integer> broadcastMessage(String message) {
        return CompletableFuture.supplyAsync(() -> {
            AtomicInteger sentCount = new AtomicInteger(0);

            connections.values().parallelStream().forEach(client -> {
                try {
                    if (client.isOpen()) {
                        client.send(message);
                        sentCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.debug("Erreur envoi message: {}", e.getMessage());
                }
            });

            return sentCount.get();
        }, connectionExecutor);
    }

    /**
     * Envoie un message a une connexion specifique.
     */
    public boolean sendMessage(String cpId, String message) {
        PerfWebSocketClient client = connections.get(cpId);
        if (client != null && client.isOpen()) {
            try {
                client.send(message);
                return true;
            } catch (Exception e) {
                log.debug("Erreur envoi a {}: {}", cpId, e.getMessage());
            }
        }
        return false;
    }

    /**
     * Execute une action sur toutes les connexions en parallele.
     */
    public CompletableFuture<Void> forEachConnection(Consumer<PerfWebSocketClient> action) {
        return CompletableFuture.runAsync(() -> {
            connections.values().parallelStream().forEach(client -> {
                try {
                    action.accept(client);
                } catch (Exception e) {
                    log.debug("Erreur action connexion: {}", e.getMessage());
                }
            });
        }, connectionExecutor);
    }

    /**
     * Ferme toutes les connexions.
     */
    public CompletableFuture<Void> closeAll() {
        running = false;
        log.info("Fermeture de {} connexions...", connections.size());

        return CompletableFuture.runAsync(() -> {
            connections.values().parallelStream().forEach(client -> {
                try {
                    client.closeBlocking();
                } catch (Exception e) {
                    // Ignore
                }
            });
            connections.clear();
            activeCount.set(0);
        }, connectionExecutor).thenRun(() -> {
            connectionExecutor.shutdown();
            scheduler.shutdown();
            try {
                connectionExecutor.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public int getActiveCount() {
        return activeCount.get();
    }

    public int getSuccessCount() {
        return successCount.get();
    }

    public int getFailedCount() {
        return failedCount.get();
    }

    public Map<String, PerfWebSocketClient> getConnections() {
        return connections;
    }

    public boolean isRunning() {
        return running;
    }

    public void stop() {
        running = false;
    }

    /**
     * Client WebSocket pour les tests de performance.
     */
    public class PerfWebSocketClient extends WebSocketClient {

        private final String cpId;
        private final long startTime;
        private long bootNotificationSentTime;
        private volatile boolean bootAccepted = false;

        public PerfWebSocketClient(URI serverUri, String cpId, long startTime) {
            super(serverUri);
            this.cpId = cpId;
            this.startTime = startTime;
            this.setConnectionLostTimeout(30);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            log.trace("WebSocket ouvert: {}", cpId);
            // Envoyer BootNotification
            bootNotificationSentTime = System.nanoTime();
            String bootMsg = String.format(
                "[2,\"%s\",\"BootNotification\",{\"chargePointVendor\":\"PerfTest\",\"chargePointModel\":\"Perf25K\"}]",
                java.util.UUID.randomUUID().toString()
            );
            send(bootMsg);
        }

        @Override
        public void onMessage(String message) {
            if (messageCallback != null) {
                long latency = (System.nanoTime() - bootNotificationSentTime) / 1_000_000;
                messageCallback.accept(new MessageEvent(cpId, message, latency, System.currentTimeMillis()));
            }

            // Detecter BootNotification accepted
            if (message.contains("\"Accepted\"") && !bootAccepted) {
                bootAccepted = true;
                long bootLatency = (System.nanoTime() - bootNotificationSentTime) / 1_000_000;
                log.trace("BootNotification accepte: {} ({}ms)", cpId, bootLatency);
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            log.trace("WebSocket ferme: {} (code={}, reason={})", cpId, code, reason);
            connections.remove(cpId);
            activeCount.decrementAndGet();
        }

        @Override
        public void onError(Exception ex) {
            log.debug("Erreur WebSocket {}: {}", cpId, ex.getMessage());
        }

        public String getCpId() {
            return cpId;
        }

        public boolean isBootAccepted() {
            return bootAccepted;
        }
    }

    /**
     * Evenement de message recu.
     */
    public static class MessageEvent {
        public final String cpId;
        public final String message;
        public final long latencyMs;
        public final long timestamp;

        public MessageEvent(String cpId, String message, long latencyMs, long timestamp) {
            this.cpId = cpId;
            this.message = message;
            this.latencyMs = latencyMs;
            this.timestamp = timestamp;
        }
    }
}
