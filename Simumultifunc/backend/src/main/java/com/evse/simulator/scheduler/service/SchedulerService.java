package com.evse.simulator.scheduler.service;

import com.evse.simulator.scheduler.model.ActionConfig;
import com.evse.simulator.scheduler.model.ScheduledTask;
import com.evse.simulator.scheduler.model.TaskExecution;
import com.evse.simulator.scheduler.repository.SchedulerRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Service de gestion des tâches planifiées.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SchedulerService {

    private final SchedulerRepository repository;
    private final TaskActionExecutor taskExecutor;

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Set<String> executingTasks = ConcurrentHashMap.newKeySet();

    // ═══════════════════════════════════════════════════════════════════
    // INITIALISATION
    // ═══════════════════════════════════════════════════════════════════

    @PostConstruct
    public void init() {
        log.info("Initializing Scheduler Service...");

        // Charger et planifier toutes les tâches actives
        var tasks = repository.findAll();
        for (var task : tasks) {
            if (task.isEnabled()) {
                scheduleTask(task);
            }
        }

        log.info("Scheduler initialized with {} active tasks", scheduledTasks.size());
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down scheduler...");
        executor.shutdownNow();
    }

    // ═══════════════════════════════════════════════════════════════════
    // VÉRIFICATION PÉRIODIQUE (pour les tâches cron)
    // ═══════════════════════════════════════════════════════════════════

    @Scheduled(fixedRate = 60000)  // Toutes les minutes
    public void checkScheduledTasks() {
        log.debug("Checking scheduled tasks...");

        Instant now = Instant.now();
        var tasks = repository.findAll();

        for (var task : tasks) {
            if (!task.isEnabled()) continue;

            // Vérifier si c'est l'heure d'exécuter (pour cron)
            if ("cron".equals(task.getScheduleType()) && shouldExecuteNow(task, now)) {
                if (!executingTasks.contains(task.getId())) {
                    log.info("Triggering cron task: {} ({})", task.getName(), task.getId());
                    executeTaskAsync(task, "schedule");
                }
            }

            // Mettre à jour nextRunAt
            updateNextRunAt(task);
        }
    }

    private boolean shouldExecuteNow(ScheduledTask task, Instant now) {
        if (task.getNextRunAt() == null) return false;

        // Tolérance de 30 secondes
        return !now.isBefore(task.getNextRunAt()) &&
            now.isBefore(task.getNextRunAt().plusSeconds(30));
    }

    // ═══════════════════════════════════════════════════════════════════
    // CRUD TÂCHES
    // ═══════════════════════════════════════════════════════════════════

    public List<ScheduledTask> getAllTasks() {
        return repository.findAll();
    }

    public Optional<ScheduledTask> getTask(String id) {
        return repository.findById(id);
    }

    public ScheduledTask createTask(ScheduledTask task) {
        task.setId(UUID.randomUUID().toString());
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        task.setRunCount(0);
        task.setFailCount(0);

        // Valider la configuration
        validateTask(task);

        // Calculer la prochaine exécution
        updateNextRunAt(task);

        repository.save(task);

        if (task.isEnabled()) {
            scheduleTask(task);
        }

        log.info("Created task: {} ({}) - next run: {}", task.getName(), task.getId(), task.getNextRunAt());
        return task;
    }

    public ScheduledTask updateTask(String id, ScheduledTask updated) {
        var existing = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));

        // Annuler l'ancienne planification
        cancelTask(id);

        // Conserver les métadonnées
        updated.setId(id);
        updated.setCreatedAt(existing.getCreatedAt());
        updated.setUpdatedAt(Instant.now());
        updated.setRunCount(existing.getRunCount());
        updated.setFailCount(existing.getFailCount());
        updated.setLastRunAt(existing.getLastRunAt());
        updated.setLastRunStatus(existing.getLastRunStatus());
        updated.setLastRunError(existing.getLastRunError());

        // Valider et recalculer
        validateTask(updated);
        updateNextRunAt(updated);

        repository.save(updated);

        if (updated.isEnabled()) {
            scheduleTask(updated);
        }

        log.info("Updated task: {} ({}) - next run: {}", updated.getName(), id, updated.getNextRunAt());
        return updated;
    }

    public void deleteTask(String id) {
        cancelTask(id);
        repository.delete(id);
        log.info("Deleted task: {}", id);
    }

    public void setTaskEnabled(String id, boolean enabled) {
        var task = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));

        task.setEnabled(enabled);
        task.setUpdatedAt(Instant.now());

        if (enabled) {
            updateNextRunAt(task);
            scheduleTask(task);
        } else {
            cancelTask(id);
            task.setNextRunAt(null);
        }

        repository.save(task);
        log.info("Task {} {}", id, enabled ? "enabled" : "disabled");
    }

    private void validateTask(ScheduledTask task) {
        if (task.getName() == null || task.getName().isBlank()) {
            throw new IllegalArgumentException("Task name is required");
        }
        if (task.getScheduleType() == null) {
            throw new IllegalArgumentException("Schedule type is required");
        }
        if (task.getActionType() == null) {
            throw new IllegalArgumentException("Action type is required");
        }
        if (task.getActionConfig() == null) {
            throw new IllegalArgumentException("Action config is required");
        }

        // Valider selon le type de planification
        switch (task.getScheduleType()) {
            case "cron" -> {
                if (task.getCronExpression() == null || task.getCronExpression().isBlank()) {
                    throw new IllegalArgumentException("Cron expression is required");
                }
                try {
                    CronExpression.parse(task.getCronExpression());
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid cron expression: " + e.getMessage());
                }
            }
            case "interval" -> {
                if (task.getIntervalSeconds() == null || task.getIntervalSeconds() < 10) {
                    throw new IllegalArgumentException("Interval must be at least 10 seconds");
                }
            }
            case "once" -> {
                if (task.getRunAt() == null) {
                    throw new IllegalArgumentException("runAt is required for once schedule");
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PLANIFICATION
    // ═══════════════════════════════════════════════════════════════════

    private void scheduleTask(ScheduledTask task) {
        // Annuler si déjà planifiée
        cancelTask(task.getId());

        switch (task.getScheduleType()) {
            case "interval" -> scheduleInterval(task);
            case "once" -> scheduleOnce(task);
            case "cron" -> {
                // Les tâches cron sont gérées par le check périodique
                log.debug("Cron task {} will be checked periodically", task.getName());
            }
        }
    }

    private void scheduleInterval(ScheduledTask task) {
        if (task.getIntervalSeconds() == null || task.getIntervalSeconds() <= 0) {
            log.warn("Invalid interval for task {}", task.getId());
            return;
        }

        var future = executor.scheduleAtFixedRate(
            () -> executeTaskAsync(task, "schedule"),
            task.getIntervalSeconds(),
            task.getIntervalSeconds(),
            TimeUnit.SECONDS
        );

        scheduledTasks.put(task.getId(), future);
        log.info("Scheduled interval task: {} every {}s", task.getName(), task.getIntervalSeconds());
    }

    private void scheduleOnce(ScheduledTask task) {
        if (task.getRunAt() == null) {
            log.warn("No runAt for once task {}", task.getId());
            return;
        }

        long delayMs = Duration.between(Instant.now(), task.getRunAt()).toMillis();
        if (delayMs <= 0) {
            log.warn("Task {} runAt is in the past", task.getId());
            return;
        }

        var future = executor.schedule(
            () -> executeTaskAsync(task, "schedule"),
            delayMs,
            TimeUnit.MILLISECONDS
        );

        scheduledTasks.put(task.getId(), future);
        log.info("Scheduled once task: {} at {}", task.getName(), task.getRunAt());
    }

    private void cancelTask(String taskId) {
        var future = scheduledTasks.remove(taskId);
        if (future != null) {
            future.cancel(false);
            log.debug("Cancelled task: {}", taskId);
        }
    }

    private void updateNextRunAt(ScheduledTask task) {
        Instant nextRun = null;

        switch (task.getScheduleType()) {
            case "interval" -> {
                if (task.getIntervalSeconds() != null) {
                    if (task.getLastRunAt() != null) {
                        nextRun = task.getLastRunAt().plusSeconds(task.getIntervalSeconds());
                    } else {
                        nextRun = Instant.now().plusSeconds(task.getIntervalSeconds());
                    }
                }
            }
            case "once" -> {
                if (task.getRunAt() != null && task.getRunAt().isAfter(Instant.now())) {
                    nextRun = task.getRunAt();
                }
            }
            case "cron" -> {
                nextRun = calculateNextCronRun(task.getCronExpression(), task.getTimezone());
            }
        }

        task.setNextRunAt(nextRun);
    }

    private Instant calculateNextCronRun(String cronExpr, String timezone) {
        try {
            var cron = CronExpression.parse(cronExpr);
            var zoneId = timezone != null ? ZoneId.of(timezone) : ZoneId.systemDefault();
            var now = ZonedDateTime.now(zoneId);
            var next = cron.next(now.toLocalDateTime());

            if (next != null) {
                return next.atZone(zoneId).toInstant();
            }
        } catch (Exception e) {
            log.error("Invalid cron expression: {}", cronExpr, e);
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════
    // EXÉCUTION
    // ═══════════════════════════════════════════════════════════════════

    private void executeTaskAsync(ScheduledTask task, String triggeredBy) {
        // Éviter les exécutions multiples simultanées
        if (!executingTasks.add(task.getId())) {
            log.warn("Task {} is already executing, skipping", task.getId());
            return;
        }

        try {
            executeTask(task, triggeredBy);
        } finally {
            executingTasks.remove(task.getId());
        }
    }

    public TaskExecution executeTask(ScheduledTask task, String triggeredBy) {
        log.info("Executing task: {} ({}) triggered by {}",
            task.getName(), task.getId(), triggeredBy);

        var execution = TaskExecution.builder()
            .id(UUID.randomUUID().toString())
            .taskId(task.getId())
            .taskName(task.getName())
            .startedAt(Instant.now())
            .status("running")
            .triggeredBy(triggeredBy)
            .build();

        repository.saveExecution(execution);

        // Mettre à jour le statut de la tâche
        task.setLastRunAt(Instant.now());
        task.setLastRunStatus("running");
        task.setRunCount(task.getRunCount() + 1);

        try {
            // Exécuter l'action
            String output = taskExecutor.execute(task.getActionType(), task.getActionConfig());

            // Succès
            execution.setStatus("success");
            execution.setOutput(output);
            task.setLastRunStatus("success");
            task.setLastRunError(null);

            log.info("Task {} completed successfully: {}", task.getName(), output);

        } catch (Exception e) {
            // Échec
            execution.setStatus("failed");
            execution.setError(e.getMessage());
            task.setLastRunStatus("failed");
            task.setLastRunError(e.getMessage());
            task.setFailCount(task.getFailCount() + 1);

            log.error("Task {} failed: {}", task.getName(), e.getMessage(), e);
        }

        execution.setCompletedAt(Instant.now());
        execution.setDurationMs(
            Duration.between(execution.getStartedAt(), execution.getCompletedAt()).toMillis()
        );

        // Mettre à jour nextRunAt
        updateNextRunAt(task);

        repository.save(task);
        repository.saveExecution(execution);

        return execution;
    }

    public TaskExecution runTaskManually(String taskId) {
        var task = repository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        return executeTask(task, "manual");
    }

    // ═══════════════════════════════════════════════════════════════════
    // HISTORIQUE
    // ═══════════════════════════════════════════════════════════════════

    public List<TaskExecution> getExecutions(String taskId, int limit) {
        return repository.findExecutions(taskId, limit);
    }

    public List<TaskExecution> getRecentExecutions(int limit) {
        return repository.findRecentExecutions(limit);
    }
}
