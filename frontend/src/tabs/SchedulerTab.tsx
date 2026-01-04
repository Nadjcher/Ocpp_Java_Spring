// frontend/src/tabs/SchedulerTab.tsx
import React, { useEffect, useState, useCallback } from "react";
import { config } from "@/config/env";

/* ========================================================================== */
/* Types                                                                       */
/* ========================================================================== */

interface ScheduledTask {
    id: string;
    name: string;
    sessionId: string;
    action: string;
    cronExpression?: string;
    scheduledTime?: string;
    enabled: boolean;
    lastRun?: string;
    nextRun?: string;
    status: "pending" | "running" | "completed" | "failed";
}

interface Session {
    id: string;
    cpId: string;
    state: string;
}

/* ========================================================================== */
/* Config                                                                      */
/* ========================================================================== */

const API_BASE = (() => {
    const isDev = typeof import.meta !== "undefined" && import.meta.env?.DEV;
    if (isDev) return "";
    if (typeof window !== 'undefined') {
        const stored = window.localStorage.getItem("runner_api");
        if (stored) return stored;
    }
    return config.apiUrl || "http://localhost:8887";
})();

const ACTIONS = [
    { value: "connect", label: "Connecter" },
    { value: "boot", label: "BootNotification" },
    { value: "authorize", label: "Authorize" },
    { value: "startTx", label: "Demarrer Transaction" },
    { value: "stopTx", label: "Arreter Transaction" },
    { value: "disconnect", label: "Deconnecter" },
];

/* ========================================================================== */
/* Composant principal                                                         */
/* ========================================================================== */

export default function SchedulerTab() {
    const [tasks, setTasks] = useState<ScheduledTask[]>([]);
    const [sessions, setSessions] = useState<Session[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // Formulaire nouvelle tache
    const [newTask, setNewTask] = useState({
        name: "",
        sessionId: "",
        action: "connect",
        scheduledTime: "",
        enabled: true,
    });

    // Charger les sessions disponibles
    const fetchSessions = useCallback(async () => {
        try {
            const res = await fetch(`${API_BASE}/api/simu`);
            if (res.ok) {
                const data = await res.json();
                setSessions(Array.isArray(data) ? data : data.sessions || []);
            }
        } catch (e) {
            console.error("Failed to fetch sessions:", e);
        }
    }, []);

    // Charger les taches planifiees (stub - a implementer cote backend)
    const fetchTasks = useCallback(async () => {
        // Pour l'instant, utiliser localStorage comme stockage temporaire
        const stored = localStorage.getItem("scheduler_tasks");
        if (stored) {
            try {
                setTasks(JSON.parse(stored));
            } catch {
                setTasks([]);
            }
        }
    }, []);

    useEffect(() => {
        fetchSessions();
        fetchTasks();
    }, [fetchSessions, fetchTasks]);

    // Sauvegarder les taches
    const saveTasks = (updatedTasks: ScheduledTask[]) => {
        setTasks(updatedTasks);
        localStorage.setItem("scheduler_tasks", JSON.stringify(updatedTasks));
    };

    // Ajouter une tache
    const addTask = () => {
        if (!newTask.name || !newTask.sessionId || !newTask.scheduledTime) {
            setError("Veuillez remplir tous les champs obligatoires");
            return;
        }

        const task: ScheduledTask = {
            id: `task-${Date.now()}`,
            name: newTask.name,
            sessionId: newTask.sessionId,
            action: newTask.action,
            scheduledTime: newTask.scheduledTime,
            enabled: newTask.enabled,
            status: "pending",
            nextRun: newTask.scheduledTime,
        };

        saveTasks([...tasks, task]);
        setNewTask({
            name: "",
            sessionId: "",
            action: "connect",
            scheduledTime: "",
            enabled: true,
        });
        setError(null);
    };

    // Supprimer une tache
    const deleteTask = (taskId: string) => {
        saveTasks(tasks.filter(t => t.id !== taskId));
    };

    // Activer/desactiver une tache
    const toggleTask = (taskId: string) => {
        saveTasks(tasks.map(t =>
            t.id === taskId ? { ...t, enabled: !t.enabled } : t
        ));
    };

    // Executer une tache manuellement
    const executeTask = async (task: ScheduledTask) => {
        setIsLoading(true);
        try {
            const res = await fetch(`${API_BASE}/api/simu/${task.sessionId}/${task.action}`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
            });

            if (res.ok) {
                saveTasks(tasks.map(t =>
                    t.id === task.id
                        ? { ...t, status: "completed", lastRun: new Date().toISOString() }
                        : t
                ));
            } else {
                saveTasks(tasks.map(t =>
                    t.id === task.id ? { ...t, status: "failed" } : t
                ));
            }
        } catch (e) {
            saveTasks(tasks.map(t =>
                t.id === task.id ? { ...t, status: "failed" } : t
            ));
        } finally {
            setIsLoading(false);
        }
    };

    const getStatusStyle = (status: ScheduledTask["status"]) => {
        switch (status) {
            case "completed": return "bg-green-100 text-green-700";
            case "failed": return "bg-red-100 text-red-700";
            case "running": return "bg-blue-100 text-blue-700";
            default: return "bg-gray-100 text-gray-600";
        }
    };

    /* ======== Rendu ======== */

    return (
        <div className="p-6 space-y-6 max-w-5xl mx-auto">
            {/* Header */}
            <div>
                <h1 className="text-xl font-bold text-gray-900">Planificateur de Taches</h1>
                <p className="text-sm text-gray-500">Automatisez vos operations OCPP</p>
            </div>

            {error && (
                <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded">
                    {error}
                </div>
            )}

            {/* Formulaire nouvelle tache */}
            <div className="bg-white border rounded-lg p-5 shadow-sm">
                <h2 className="text-lg font-semibold text-gray-800 mb-4">Nouvelle Tache</h2>

                <div className="grid grid-cols-2 gap-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Nom de la tache *
                        </label>
                        <input
                            type="text"
                            value={newTask.name}
                            onChange={e => setNewTask(t => ({ ...t, name: e.target.value }))}
                            className="w-full px-3 py-2 border rounded text-sm"
                            placeholder="Ex: Demarrage matinal"
                        />
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Session *
                        </label>
                        <select
                            value={newTask.sessionId}
                            onChange={e => setNewTask(t => ({ ...t, sessionId: e.target.value }))}
                            className="w-full px-3 py-2 border rounded text-sm"
                        >
                            <option value="">-- Selectionner --</option>
                            {sessions.map(s => (
                                <option key={s.id} value={s.id}>
                                    {s.cpId} ({s.state})
                                </option>
                            ))}
                        </select>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Action
                        </label>
                        <select
                            value={newTask.action}
                            onChange={e => setNewTask(t => ({ ...t, action: e.target.value }))}
                            className="w-full px-3 py-2 border rounded text-sm"
                        >
                            {ACTIONS.map(a => (
                                <option key={a.value} value={a.value}>{a.label}</option>
                            ))}
                        </select>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Date/Heure *
                        </label>
                        <input
                            type="datetime-local"
                            value={newTask.scheduledTime}
                            onChange={e => setNewTask(t => ({ ...t, scheduledTime: e.target.value }))}
                            className="w-full px-3 py-2 border rounded text-sm"
                        />
                    </div>
                </div>

                <div className="mt-4 flex items-center justify-between">
                    <label className="flex items-center gap-2 text-sm">
                        <input
                            type="checkbox"
                            checked={newTask.enabled}
                            onChange={e => setNewTask(t => ({ ...t, enabled: e.target.checked }))}
                            className="rounded"
                        />
                        Activer immediatement
                    </label>

                    <button
                        onClick={addTask}
                        className="px-4 py-2 bg-blue-600 text-white text-sm rounded hover:bg-blue-700"
                    >
                        Ajouter la tache
                    </button>
                </div>
            </div>

            {/* Liste des taches */}
            <div className="bg-white border rounded-lg p-5 shadow-sm">
                <h2 className="text-lg font-semibold text-gray-800 mb-4">
                    Taches planifiees ({tasks.length})
                </h2>

                {tasks.length === 0 ? (
                    <div className="text-center py-8 text-gray-500">
                        Aucune tache planifiee. Creez-en une ci-dessus.
                    </div>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead className="bg-gray-50">
                                <tr>
                                    <th className="text-left px-4 py-2">Nom</th>
                                    <th className="text-left px-4 py-2">Session</th>
                                    <th className="text-left px-4 py-2">Action</th>
                                    <th className="text-left px-4 py-2">Planifie</th>
                                    <th className="text-center px-4 py-2">Statut</th>
                                    <th className="text-center px-4 py-2">Actif</th>
                                    <th className="text-right px-4 py-2">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y">
                                {tasks.map(task => {
                                    const session = sessions.find(s => s.id === task.sessionId);
                                    return (
                                        <tr key={task.id} className="hover:bg-gray-50">
                                            <td className="px-4 py-3 font-medium">{task.name}</td>
                                            <td className="px-4 py-3">
                                                {session?.cpId || task.sessionId.slice(0, 8)}
                                            </td>
                                            <td className="px-4 py-3">
                                                {ACTIONS.find(a => a.value === task.action)?.label}
                                            </td>
                                            <td className="px-4 py-3 text-gray-600">
                                                {task.scheduledTime
                                                    ? new Date(task.scheduledTime).toLocaleString()
                                                    : "-"}
                                            </td>
                                            <td className="px-4 py-3 text-center">
                                                <span className={`px-2 py-1 rounded text-xs font-medium ${getStatusStyle(task.status)}`}>
                                                    {task.status}
                                                </span>
                                            </td>
                                            <td className="px-4 py-3 text-center">
                                                <button
                                                    onClick={() => toggleTask(task.id)}
                                                    className={`w-10 h-5 rounded-full transition-colors ${
                                                        task.enabled ? "bg-green-500" : "bg-gray-300"
                                                    }`}
                                                >
                                                    <span className={`block w-4 h-4 bg-white rounded-full shadow transform transition-transform ${
                                                        task.enabled ? "translate-x-5" : "translate-x-0.5"
                                                    }`} />
                                                </button>
                                            </td>
                                            <td className="px-4 py-3 text-right space-x-2">
                                                <button
                                                    onClick={() => executeTask(task)}
                                                    disabled={isLoading}
                                                    className="px-2 py-1 text-xs bg-blue-100 text-blue-700 rounded hover:bg-blue-200 disabled:opacity-50"
                                                >
                                                    Executer
                                                </button>
                                                <button
                                                    onClick={() => deleteTask(task.id)}
                                                    className="px-2 py-1 text-xs bg-red-100 text-red-700 rounded hover:bg-red-200"
                                                >
                                                    Supprimer
                                                </button>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>

            {/* Note */}
            <div className="bg-yellow-50 border border-yellow-200 text-yellow-800 px-4 py-3 rounded text-sm">
                <strong>Note:</strong> Les taches sont actuellement stockees localement.
                L'execution automatique necessite une implementation backend.
            </div>
        </div>
    );
}
