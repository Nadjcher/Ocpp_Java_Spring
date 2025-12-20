package com.evse.simulator.scheduler.repository;

import com.evse.simulator.scheduler.model.ScheduledTask;
import com.evse.simulator.scheduler.model.TaskExecution;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Repository pour les tâches planifiées (persistance JSON).
 */
@Repository
@Slf4j
public class SchedulerRepository {

    @Value("${scheduler.data.path:data/scheduler}")
    private String dataPath;

    private final ObjectMapper mapper;
    private final Map<String, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private final List<TaskExecution> executions = Collections.synchronizedList(new ArrayList<>());

    private static final int MAX_EXECUTIONS = 500;

    public SchedulerRepository() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    public void init() {
        try {
            File dir = new File(dataPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            loadTasks();
            loadExecutions();
            log.info("Scheduler repository initialized: {} tasks, {} executions",
                tasks.size(), executions.size());
        } catch (Exception e) {
            log.error("Failed to initialize scheduler repository", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TASKS CRUD
    // ═══════════════════════════════════════════════════════════════════

    public List<ScheduledTask> findAll() {
        return new ArrayList<>(tasks.values());
    }

    public Optional<ScheduledTask> findById(String id) {
        return Optional.ofNullable(tasks.get(id));
    }

    public ScheduledTask save(ScheduledTask task) {
        tasks.put(task.getId(), task);
        persistTasks();
        return task;
    }

    public void delete(String id) {
        tasks.remove(id);
        persistTasks();
    }

    // ═══════════════════════════════════════════════════════════════════
    // EXECUTIONS
    // ═══════════════════════════════════════════════════════════════════

    public TaskExecution saveExecution(TaskExecution execution) {
        executions.add(0, execution);

        // Limiter le nombre d'exécutions
        while (executions.size() > MAX_EXECUTIONS) {
            executions.remove(executions.size() - 1);
        }

        persistExecutions();
        return execution;
    }

    public List<TaskExecution> findExecutions(String taskId, int limit) {
        return executions.stream()
            .filter(e -> taskId == null || taskId.equals(e.getTaskId()))
            .limit(limit)
            .collect(Collectors.toList());
    }

    public List<TaskExecution> findRecentExecutions(int limit) {
        return executions.stream()
            .limit(limit)
            .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════
    // PERSISTENCE
    // ═══════════════════════════════════════════════════════════════════

    private void loadTasks() {
        File file = new File(dataPath, "tasks.json");
        if (file.exists()) {
            try {
                List<ScheduledTask> loaded = mapper.readValue(file,
                    new TypeReference<List<ScheduledTask>>() {});
                for (ScheduledTask task : loaded) {
                    tasks.put(task.getId(), task);
                }
                log.info("Loaded {} scheduled tasks", tasks.size());
            } catch (IOException e) {
                log.error("Failed to load tasks", e);
            }
        }
    }

    private void loadExecutions() {
        File file = new File(dataPath, "executions.json");
        if (file.exists()) {
            try {
                List<TaskExecution> loaded = mapper.readValue(file,
                    new TypeReference<List<TaskExecution>>() {});
                executions.addAll(loaded);
                log.info("Loaded {} executions", executions.size());
            } catch (IOException e) {
                log.error("Failed to load executions", e);
            }
        }
    }

    private synchronized void persistTasks() {
        try {
            File file = new File(dataPath, "tasks.json");
            mapper.writeValue(file, new ArrayList<>(tasks.values()));
        } catch (IOException e) {
            log.error("Failed to persist tasks", e);
        }
    }

    private synchronized void persistExecutions() {
        try {
            File file = new File(dataPath, "executions.json");
            mapper.writeValue(file, executions);
        } catch (IOException e) {
            log.error("Failed to persist executions", e);
        }
    }
}
