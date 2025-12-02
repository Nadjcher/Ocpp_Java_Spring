/** @type {import('tailwindcss').Config} */
module.exports = {
    content: ["./index.html", "./src/**/*.{ts,tsx,js,jsx}"],
    theme: {
        extend: {
            // Couleurs personnalisées EVSE Simulator
            colors: {
                // Couleurs de fond
                'sky-light': '#eaf6ff',
                'surface': '#f8fafc',

                // Couleurs de navigation
                'nav': {
                    DEFAULT: '#0b2545',
                    dark: '#112e51',
                    hover: 'rgba(255,255,255,0.06)',
                    active: '#1e3a8a',
                },

                // Couleurs de statut EVSE
                'evse': {
                    available: '#22c55e',      // vert
                    preparing: '#facc15',      // jaune
                    charging: '#3b82f6',       // bleu
                    finishing: '#f97316',      // orange
                    unavailable: '#6b7280',    // gris
                    faulted: '#ef4444',        // rouge
                },

                // Alias pratiques
                'primary': {
                    DEFAULT: '#2563eb',
                    50: '#eff6ff',
                    100: '#dbeafe',
                    200: '#bfdbfe',
                    300: '#93c5fd',
                    400: '#60a5fa',
                    500: '#3b82f6',
                    600: '#2563eb',
                    700: '#1d4ed8',
                    800: '#1e40af',
                    900: '#1e3a8a',
                },
            },

            // Espacements personnalisés
            spacing: {
                '18': '4.5rem',
                '88': '22rem',
            },

            // Ombres personnalisées
            boxShadow: {
                'card': '0 2px 10px rgba(0, 0, 0, 0.06)',
                'card-lg': '0 4px 20px rgba(0, 0, 0, 0.1)',
                'glow-blue': '0 0 20px rgba(59, 130, 246, 0.3)',
                'glow-green': '0 0 20px rgba(34, 197, 94, 0.3)',
            },

            // Border radius personnalisés
            borderRadius: {
                'xl': '12px',
                '2xl': '16px',
            },

            // Fonts
            fontFamily: {
                'sans': ['Inter', 'system-ui', 'Arial', 'sans-serif'],
                'mono': ['ui-monospace', 'SFMono-Regular', 'Menlo', 'Consolas', 'Liberation Mono', 'monospace'],
            },

            // Animations personnalisées
            animation: {
                'pulse-slow': 'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
                'spin-slow': 'spin 2s linear infinite',
                'fade-in': 'fadeIn 0.3s ease-in-out',
                'slide-up': 'slideUp 0.3s ease-out',
            },
            keyframes: {
                fadeIn: {
                    '0%': { opacity: '0' },
                    '100%': { opacity: '1' },
                },
                slideUp: {
                    '0%': { transform: 'translateY(10px)', opacity: '0' },
                    '100%': { transform: 'translateY(0)', opacity: '1' },
                },
            },

            // Tailles min/max
            minHeight: {
                'screen-nav': 'calc(100vh - 48px)',
            },
            maxWidth: {
                '8xl': '88rem',
            },
        },
    },
    plugins: [],
};
