# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
npm run dev        # Vite dev server (http://localhost:5173/carburants-france/)
npm run build      # tsc -b && vite build → dist/
npm run lint       # ESLint (flat config)
npm run preview    # Preview built dist/ locally
node scripts/process-data.mjs    # Refresh fuel data from gouv.fr API
node scripts/generate-pwa-icons.mjs    # After editing public/pwa-icon-source.svg
```

Note: `base` is `/carburants-france/` (GitHub Pages subpath), so dev server serves at that path.

## Local setup

For local dev, copy `.env.example` to `.env.local` and fill in the values you actually need. `.env.local` is gitignored (`.gitignore` covers `.env`, `.env.local`, and `*.local`).

Currently optional (becomes required for the **UI redesign** branch when R1/U1 lands):

- `VITE_MAPTILER_KEY` — MapTiler API key for the FR-localized tile provider. Create a free account at https://cloud.maptiler.com/, restrict the key by HTTP Referrer (`*.github.io/carburants-france/*`) and configure a hard quota cap in the dashboard. Note: Vite inlines `VITE_*` vars into the public JS bundle at build time, so this key is publicly visible in production — the `.env.local` only keeps it out of source. Use the dashboard restrictions, not secrecy, as the actual abuse mitigation.

In CI, secrets are injected via the `env:` block at the build step in `.github/workflows/deploy.yml`, sourced from GitHub Actions repository secrets.

## Planning artefacts

This repo follows the compound-engineering plan/brainstorm convention:

- `docs/brainstorms/*-requirements.md` — product requirement docs (origin for plans)
- `docs/plans/*-plan.md` — implementation plans (consumed by `/ce:work`)
- `docs/solutions/*` — documented solutions to past problems and best practices, organized by category with YAML frontmatter (`module`, `tags`, `problem_type`); relevant when implementing or debugging in documented areas
- `docs/regression-checklist-*.md` — manual regression checklists attached to PRs (no test runner in this project)
- `docs/marianne-licence-a4-notes.md` — A4 pre-work notes for the UI redesign plan

## Architecture

Single-page React 19 + TypeScript app displaying French fuel station prices on a Leaflet map. No router — all state lives in `App.tsx` via hooks.

### Data Flow

1. **Fuel data pipeline** (`scripts/process-data.mjs`): Downloads XML from `donnees.roulez-eco.fr`, parses it, splits into per-department JSON files in `public/data/departments/{dept}.json` + `public/data/meta.json`. Runs every 2h via GitHub Actions.

2. **Lazy loading**: When a user selects a city, the app loads only that department + its neighbors (pre-computed adjacency in `utils/departments.ts`), then filters stations within a 15km Haversine radius.

3. **City search**: Autocomplete via `api-adresse.data.gouv.fr` (debounced 300ms). Geolocation uses the same API for reverse geocoding.

### Key Modules

| Module | Role |
|--------|------|
| `App.tsx` | Orchestrator — search, fuel filter, geolocation, station selection, toast feedback |
| `components/MapView.tsx` | Leaflet map with marker clustering, price-label DivIcons, radius circle, bounds tracking |
| `hooks/useStations.ts` | Lazy dept-based station loading with dedup (tracks loaded depts via ref) |
| `hooks/useCitySearch.ts` | Debounced city autocomplete with AbortController |
| `hooks/useMapView.ts` | Map center/zoom/bounds state + flyToCity animation |
| `utils/departments.ts` | Static adjacency graph: dept → neighbors list |
| `utils/geo.ts` | Haversine distance, filterByRadius, reverseGeocode, dept extraction from postal code |
| `utils/fuel.ts` | FUEL_COLORS, FUEL_LABELS, price formatting, sorting, timeAgo |

### Layout

- **Desktop**: Full map + right sidebar (`w-72`) with station list
- **Mobile**: Full map + expandable bottom sheet + collapsible fuel filter

### Styling

Tailwind CSS v4 (import-based, no `tailwind.config.js`). Custom theme colors for fuels defined in `@theme` block in `index.css`. `.glass` utility class for frosted-glass effect (used on overlays, header, panels).

### External APIs

- `api-adresse.data.gouv.fr` — City search + reverse geocoding
- `donnees.roulez-eco.fr` — Fuel price XML (data pipeline only)
- CARTO basemap tiles for the map layer

### Deployment

GitHub Pages via `.github/workflows/deploy.yml`. Data refresh via `.github/workflows/update-data.yml` (cron every 2h, commits updated JSONs).
