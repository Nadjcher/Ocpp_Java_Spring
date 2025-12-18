// vite.config.ts
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
    server: {
        host: '0.0.0.0',
        port: 3002,
        allowedHosts: [
            'localhost',
            '.loca.lt',
            '.ngrok-free.app',
            '.ngrok.io',
            '.ngrok.app'
        ],
        proxy: {
            "/api": {
                target: "http://localhost:8887",
                changeOrigin: true,
                ws: true,
                secure: false,
                configure: (proxy) => {
                    proxy.on('proxyReq', (proxyReq, req) => {
                        console.log(`[Proxy] ${req.method} ${req.url} -> http://localhost:8887${proxyReq.path}`);
                    });
                    proxy.on('error', (err) => {
                        console.error('[Proxy Error]', err.message);
                    });
                }
            },
            "/ws": {
                target: "ws://localhost:8887",
                changeOrigin: true,
                ws: true
            },
            "/ws-ml": {
                target: "ws://localhost:8887",
                changeOrigin: true,
                ws: true
            },
        },
    },
    resolve: {
        alias: {
            "@": path.resolve(__dirname, "src"),
        },
    },
    plugins: [react()],
});