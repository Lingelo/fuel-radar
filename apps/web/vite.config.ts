import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { VitePWA } from 'vite-plugin-pwa';

// GitHub Pages serves the site under the repository name. In dev we keep
// the root path so `npm run dev` opens at http://localhost:5174/.
const BASE = '/fuel-radar/';

export default defineConfig(({ command }) => ({
  base: command === 'build' ? BASE : '/',
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['icon.svg', 'apple-touch-icon.png'],
      manifest: {
        id: BASE,
        name: 'FuelRadar',
        short_name: 'FuelRadar',
        description:
          'Prix des carburants en France, en Espagne et au Portugal — données ouvertes officielles.',
        lang: 'fr',
        scope: BASE,
        start_url: BASE,
        display: 'standalone',
        categories: ['travel', 'navigation', 'utilities'],
        background_color: '#121212',
        theme_color: '#121212',
        // PNG icons are what Android (WebAPK) and most launchers expect;
        // the SVG stays as a crisp fallback. Regenerate the PNGs from
        // icon.svg if the logo changes.
        icons: [
          { src: 'icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
          {
            src: 'icon-maskable-512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
          { src: 'icon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any' },
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
              url.pathname.endsWith('/data/meta.json') ||
              url.pathname.endsWith('/data/dept-bbox.json'),
            handler: 'NetworkFirst',
            options: {
              cacheName: 'station-data',
              networkTimeoutSeconds: 5,
              // ~170 department files (France + Espagne + Portugal) + meta + bbox
              expiration: { maxEntries: 250, maxAgeSeconds: 24 * 60 * 60 },
            },
          },
          {
            urlPattern: ({ url }) =>
              url.pathname.includes('/data/history/') ||
              url.pathname.endsWith('/data/history.json') ||
              url.pathname.endsWith('/data/history-countries.json'),
            handler: 'NetworkFirst',
            options: {
              cacheName: 'history-data',
              networkTimeoutSeconds: 5,
              expiration: { maxEntries: 100, maxAgeSeconds: 7 * 24 * 60 * 60 },
            },
          },
          {
            urlPattern: ({ url }) =>
              url.hostname.endsWith('basemaps.cartocdn.com') ||
              url.hostname.endsWith('api.maptiler.com') ||
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
