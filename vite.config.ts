import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { VitePWA } from 'vite-plugin-pwa';

// Same base path as the original `carburants-france` project so the
// existing GitHub Pages deployment URL keeps working. In dev we keep
// the root path so `npm run dev` opens at http://localhost:5174/.
const BASE = '/carburants-france/';

export default defineConfig(({ command }) => ({
  base: command === 'build' ? BASE : '/',
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['icon.svg', 'icon-192.png.svg'],
      manifest: {
        name: 'Carburants France',
        short_name: 'Carburants',
        description:
          'Prix des carburants en France en temps réel — données prix-carburants.gouv.fr.',
        lang: 'fr',
        scope: BASE,
        start_url: BASE,
        display: 'standalone',
        background_color: '#f9f9ff',
        theme_color: '#a33900',
        icons: [
          {
            src: 'icon.svg',
            sizes: 'any',
            type: 'image/svg+xml',
            purpose: 'any',
          },
          {
            src: 'icon.svg',
            sizes: 'any',
            type: 'image/svg+xml',
            purpose: 'maskable',
          },
        ],
      },
      workbox: {
        // Don't precache the heavy data JSONs — let runtime caching
        // serve them with a network-first strategy so users always get
        // fresh prices when online and a fallback when offline.
        globPatterns: ['**/*.{js,css,html,svg,woff2}'],
        runtimeCaching: [
          {
            urlPattern: ({ url }) =>
              url.pathname.includes('/data/departments/') ||
              url.pathname.endsWith('/data/meta.json'),
            handler: 'NetworkFirst',
            options: {
              cacheName: 'station-data',
              networkTimeoutSeconds: 5,
              expiration: { maxEntries: 200, maxAgeSeconds: 24 * 60 * 60 },
            },
          },
          {
            urlPattern: ({ url }) =>
              url.pathname.includes('/data/history/') ||
              url.pathname.endsWith('/data/history.json'),
            handler: 'StaleWhileRevalidate',
            options: {
              cacheName: 'history-data',
              expiration: { maxEntries: 100, maxAgeSeconds: 7 * 24 * 60 * 60 },
            },
          },
          {
            urlPattern: ({ url }) =>
              url.hostname.endsWith('basemaps.cartocdn.com') ||
              url.hostname.endsWith('tile.openstreetmap.org'),
            handler: 'CacheFirst',
            options: {
              cacheName: 'map-tiles',
              expiration: { maxEntries: 600, maxAgeSeconds: 30 * 24 * 60 * 60 },
            },
          },
        ],
      },
    }),
  ],
}));
