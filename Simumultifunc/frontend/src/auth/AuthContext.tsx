// src/auth/AuthContext.tsx
// Context React pour l'état d'authentification
// Check unique au chargement. Pas de polling.
// Pas de token → page 401. L'utilisateur va sur EVP, refresh, c'est bon.

import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { TokenService } from './TokenService';

interface AuthState {
  isAuthenticated: boolean;
  isLoading: boolean;
}

const AuthContext = createContext<AuthState>({
  isAuthenticated: false,
  isLoading: true,
});

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    isAuthenticated: false,
    isLoading: true,
  });

  useEffect(() => {
    setState({
      isAuthenticated: TokenService.isAuthenticated(),
      isLoading: false,
    });
  }, []);

  return (
    <AuthContext.Provider value={state}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
