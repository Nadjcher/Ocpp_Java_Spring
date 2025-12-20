package com.evse.simulator.scheduler.controller;

import com.evse.simulator.dto.response.ApiResponse;
import com.evse.simulator.scheduler.model.ScheduledTask;
import com.evse.simulator.scheduler.model.TaskExecution;
import com.evse.simulator.scheduler.service.SchedulerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller REST pour les tâches planifiées.
 */
@RestController
@RequestMapping("/api/scheduler")
@Tag(name = "Scheduler", description = "Gestion des tâches planifiées")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class SchedulerController {

    private final SchedulerService schedulerService;

    // ═══════════════════════════════════════════════════════════════════
    // TASKS CRUD
    // ═══════════════════════════════════════════════════════════════════

    @GetMapping("/tasks")
    @Operation(summary = "Liste toutes les tâches planifiées")
    public ResponseEntity<ApiResponse<List<ScheduledTask>>> getTasks() {
        var tasks = schedulerService.getAllTasks();
        return ResponseEntity.ok(ApiResponse.ok(tasks));
    }

    @GetMapping("/tasks/{id}")
    @Operation(summary = "Récupère une tâche par ID")
    public ResponseEntity<ApiResponse<ScheduledTask>> getTask(@PathVariable String id) {
        return schedulerService.getTask(id)
            .map(task -> ResponseEntity.ok(ApiResponse.ok(task)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/tasks")
    @Operation(summary = "Crée une nouvelle tâche planifiée")
    public ResponseEntity<ApiResponse<ScheduledTask>> createTask(@RequestBody ScheduledTask task) {
        try {
            var created = schedulerService.createTask(task);
            return ResponseEntity.ok(ApiResponse.ok("Task created", created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/tasks/{id}")
    @Operation(summary = "Met à jour une tâche existante")
    public ResponseEntity<ApiResponse<ScheduledTask>> updateTask(
        @PathVariable String id,
        @RequestBody ScheduledTask task
    ) {
        try {
            var updated = schedulerService.updateTask(id, task);
            return ResponseEntity.ok(ApiResponse.ok("Task updated", updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/tasks/{id}")
    @Operation(summary = "Supprime une tâche")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable String id) {
        try {
            schedulerService.deleteTask(id);
            return ResponseEntity.ok(ApiResponse.ok("Task deleted", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TASK CONTROL
    // ═══════════════════════════════════════════════════════════════════

    @PostMapping("/tasks/{id}/enable")
    @Operation(summary = "Active une tâche")
    public ResponseEntity<ApiResponse<Void>> enableTask(@PathVariable String id) {
        try {
            schedulerService.setTaskEnabled(id, true);
            return ResponseEntity.ok(ApiResponse.ok("Task enabled", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/tasks/{id}/disable")
    @Operation(summary = "Désactive une tâche")
    public ResponseEntity<ApiResponse<Void>> disableTask(@PathVariable String id) {
        try {
            schedulerService.setTaskEnabled(id, false);
            return ResponseEntity.ok(ApiResponse.ok("Task disabled", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/tasks/{id}/run")
    @Operation(summary = "Exécute manuellement une tâche")
    public ResponseEntity<ApiResponse<TaskExecution>> runTask(@PathVariable String id) {
        try {
            var execution = schedulerService.runTaskManually(id);
            return ResponseEntity.ok(ApiResponse.ok("Task executed", execution));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EXECUTIONS
    // ═══════════════════════════════════════════════════════════════════

    @GetMapping("/executions")
    @Operation(summary = "Historique des exécutions")
    public ResponseEntity<ApiResponse<List<TaskExecution>>> getExecutions(
        @RequestParam(required = false) String taskId,
        @RequestParam(defaultValue = "50") int limit
    ) {
        List<TaskExecution> executions;
        if (taskId != null && !taskId.isBlank()) {
            executions = schedulerService.getExecutions(taskId, limit);
        } else {
            executions = schedulerService.getRecentExecutions(limit);
        }
        return ResponseEntity.ok(ApiResponse.ok(executions));
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATUS
    // ═══════════════════════════════════════════════════════════════════

    @GetMapping("/status")
    @Operation(summary = "Statut du scheduler")
    public ResponseEntity<Map<String, Object>> getStatus() {
        var tasks = schedulerService.getAllTasks();
        var executions = schedulerService.getRecentExecutions(10);

        long enabledCount = tasks.stream().filter(ScheduledTask::isEnabled).count();
        long runningCount = executions.stream().filter(e -> "running".equals(e.getStatus())).count();

        return ResponseEntity.ok(Map.of(
            "totalTasks", tasks.size(),
            "enabledTasks", enabledCount,
            "runningExecutions", runningCount,
            "recentExecutions", executions
        ));
    }
}
