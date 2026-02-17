// src/auth/AuthContext.tsx
// Context React pour l'état d'authentification
// Pas de login custom - redirection vers EVP si non authentifié

import { createContext, useContext, useState, useCallback, useEffect, ReactNode } from 'react';
import { TokenService } from './TokenService';

interface AuthState {
  isAuthenticated: boolean;
  isLoading: boolean;
  tokenInfo: { exp: number; sub?: string; email?: string } | null;
}

interface AuthContextType extends AuthState {
  checkAuth: () => void;
  redirectToLogin: () => void;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    isAuthenticated: false,
    isLoading: true,
    tokenInfo: null,
  });

  const checkAuth = useCallback(() => {
    const isAuth = TokenService.isAuthenticated();
    const info = TokenService.getTokenInfo();
    setState({
      isAuthenticated: isAuth,
      isLoading: false,
      tokenInfo: info,
    });
  }, []);

  const redirectToLogin = useCallback(() => {
    TokenService.redirectToEvpLogin();
  }, []);

  useEffect(() => {
    checkAuth();
  }, [checkAuth]);

  // Vérifier périodiquement l'expiration du token
  useEffect(() => {
    if (!state.isAuthenticated) return;

    const interval = setInterval(() => {
      if (!TokenService.isAuthenticated()) {
        // Token expiré -> rediriger vers EVP
        TokenService.redirectToEvpLogin();
      }
    }, 60000); // Vérifier toutes les minutes

    return () => clearInterval(interval);
  }, [state.isAuthenticated]);

  return (
    <AuthContext.Provider value={{ ...state, checkAuth, redirectToLogin }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}
