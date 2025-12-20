// frontend/src/contexts/SessionsContext.tsx
import React, { createContext, useContext, useEffect, ReactNode } from "react";
import { useSessionStore } from "@/store/sessionStore";

interface SessionsContextType {
  isLoaded: boolean;
  sessionCount: number;
  activeSessionId: string | null;
}

const SessionsContext = createContext<SessionsContextType | undefined>(undefined);

export function SessionsProvider({ children }: { children: ReactNode }) {
  const { sessions, activeSessionId, loadSessions, isRehydrated } = useSessionStore();

  // Charger les sessions au montage
  useEffect(() => {
    if (isRehydrated) {
      loadSessions();
    }
  }, [isRehydrated, loadSessions]);

  const value: SessionsContextType = {
    isLoaded: isRehydrated,
    sessionCount: sessions.length,
    activeSessionId,
  };

  return (
    <SessionsContext.Provider value={value}>
      {children}
    </SessionsContext.Provider>
  );
}

export function useSessionsContext() {
  const context = useContext(SessionsContext);
  if (!context) {
    throw new Error("useSessionsContext must be used within SessionsProvider");
  }
  return context;
}
