package com.evse.simulator.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration des pools de threads pour les opérations asynchrones.
 * <p>
 * Optimisé pour supporter 25 000+ connexions WebSocket simultanées
 * avec des opérations non-bloquantes.
 * </p>
 */
@Configuration
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    @Value("${performance.async.core-pool-size:50}")
    private int corePoolSize;

    @Value("${performance.async.max-pool-size:200}")
    private int maxPoolSize;

    @Value("${performance.async.queue-capacity:10000}")
    private int queueCapacity;

    @Value("${performance.async.thread-name-prefix:evse-async-}")
    private String threadNamePrefix;

    /**
     * Executor principal pour les opérations @Async.
     * <p>
     * Configuration optimisée pour les tâches courtes et fréquentes
     * comme les mises à jour de sessions et l'envoi de messages WebSocket.
     * </p>
     */
    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);

        // Politique de rejet : exécuter dans le thread appelant si la queue est pleine
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Attendre la fin des tâches lors de l'arrêt
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        // Permettre le timeout des threads core
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(120);

        executor.initialize();

        log.info("Async executor configured: core={}, max={}, queue={}",
                corePoolSize, maxPoolSize, queueCapacity);

        return executor;
    }

    /**
     * Executor dédié aux opérations WebSocket.
     * <p>
     * Pool séparé pour éviter que les opérations WebSocket
     * ne bloquent les autres tâches asynchrones.
     * </p>
     */
    @Bean(name = "websocketExecutor")
    public Executor websocketExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(corePoolSize / 2);
        executor.setMaxPoolSize(maxPoolSize / 2);
        executor.setQueueCapacity(queueCapacity * 2);
        executor.setThreadNamePrefix("evse-ws-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info("WebSocket executor configured: core={}, max={}",
                corePoolSize / 2, maxPoolSize / 2);

        return executor;
    }

    /**
     * Executor dédié aux opérations OCPP.
     * <p>
     * Pool séparé pour les communications OCPP avec les CSMS.
     * </p>
     */
    @Bean(name = "ocppExecutor")
    public Executor ocppExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("evse-ocpp-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        log.info("OCPP executor configured: core={}, max={}", corePoolSize, maxPoolSize);

        return executor;
    }

    /**
     * Scheduler pour les tâches planifiées (heartbeat, meter values, etc.).
     */
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        scheduler.setPoolSize(20);
        scheduler.setThreadNamePrefix("evse-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setErrorHandler(t -> log.error("Scheduled task error", t));

        log.info("Task scheduler configured with pool size: 20");

        return scheduler;
    }

    /**
     * Handler pour les exceptions non capturées dans les tâches @Async.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncExceptionHandler();
    }

    /**
     * Handler d'exceptions asynchrones personnalisé.
     */
    private static class AsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            log.error("Async exception in method '{}': {}",
                    method.getName(), ex.getMessage(), ex);
        }
    }

    // =========================================================================
    // Virtual Threads (Java 21) - Spring Boot 3.5
    // NOTE: Uncomment when Java 21 is installed
    // =========================================================================

    /*
     * Virtual Threads require Java 21+. Uncomment these beans when upgrading to Java 21:
     *
     * @Bean("virtualThreadExecutor")
     * @ConditionalOnProperty(name = "spring.threads.virtual.enabled", havingValue = "true")
     * public AsyncTaskExecutor virtualThreadExecutor() {
     *     log.info("Virtual Threads executor enabled (Java 21)");
     *     return new TaskExecutorAdapter(
     *         Executors.newVirtualThreadPerTaskExecutor()
     *     );
     * }
     *
     * @Bean("ocppVirtualExecutor")
     * @ConditionalOnProperty(name = "spring.threads.virtual.enabled", havingValue = "true")
     * public AsyncTaskExecutor ocppVirtualExecutor() {
     *     log.info("OCPP Virtual Threads executor enabled");
     *     return new TaskExecutorAdapter(
     *         Executors.newThreadPerTaskExecutor(
     *             Thread.ofVirtual().name("ocpp-vt-", 0).factory()
     *         )
     *     );
     * }
     */
}