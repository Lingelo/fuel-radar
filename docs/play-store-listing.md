# Play Store listing — FuelRadar

Ready-to-paste store listing copy in FR / EN / ES / PT.

Play Console limits: **title ≤ 30 chars**, **short description ≤ 80 chars**,
**full description ≤ 4000 chars**. Counts below are within limits.

Package: `fr.fuelradar` · Category: Maps & Navigation (or Auto & Vehicles) ·
Content rating: Everyone · Ads: No · In-app purchases: No.

---

## Français (fr-FR)

**Titre**
```
FuelRadar
```

**Description courte** (64)
```
Prix des carburants en temps réel en France, Espagne et Portugal.
```

**Description complète**
```
FuelRadar vous aide à trouver la station la moins chère autour de vous, en France, en Espagne et au Portugal — à partir de données publiques officielles, gratuitement et sans publicité.

FONCTIONNALITÉS
• Carte interactive avec le prix affiché directement sur chaque station
• Recherche par adresse ou géolocalisation, avec rayon réglable
• Liste triée par prix ou par distance, filtrable par marque et 24h/24
• Détail d'une station : tous les carburants, tendance sur 7 jours, services, itinéraire
• Tendances des prix par pays (30 jours, 90 jours, 1 an) avec graphique interactif
• Favoris pour retrouver vos stations habituelles
• Tous les carburants : Gazole, SP95, SP95-E10, SP98, E85, GPLc
• Alerte « données anciennes » quand un prix n'a pas été mis à jour récemment
• Application en français, anglais, espagnol et portugais

SOURCES DE DONNÉES
Les prix proviennent des données publiques officielles : prix-carburants.gouv.fr (France), Ministerio para la Transición Ecológica (Espagne) et DGEG (Portugal). Les marques sont enrichies via OpenStreetMap.

CONFIDENTIALITÉ
Aucun compte requis. Votre position sert uniquement à centrer la carte sur votre appareil ; elle n'est ni stockée ni partagée.
```

---

## English (en-US)

**Title**
```
FuelRadar
```

**Short description** (56)
```
Real-time fuel prices across France, Spain and Portugal.
```

**Full description**
```
FuelRadar helps you find the cheapest gas station near you across France, Spain and Portugal — from official public data, free and ad-free.

FEATURES
• Interactive map with the price shown right on each station
• Search by address or use your location, with an adjustable radius
• List sorted by price or distance, filterable by brand and 24/7
• Station detail: every fuel, 7-day trend, services, directions
• Country price trends (30 days, 90 days, 1 year) with an interactive chart
• Favorites to keep your usual stations one tap away
• All fuels: Diesel, SP95, SP95-E10, SP98, E85, LPG
• "Outdated data" warning when a price hasn't been refreshed recently
• Available in French, English, Spanish and Portuguese

DATA SOURCES
Prices come from official public data: prix-carburants.gouv.fr (France), Ministerio para la Transición Ecológica (Spain) and DGEG (Portugal). Brands are enriched via OpenStreetMap.

PRIVACY
No account needed. Your location is only used to center the map on your device; it is never stored or shared.
```

---

## Español (es-ES)

**Título**
```
FuelRadar
```

**Descripción corta** (67)
```
Precios de combustible en tiempo real en Francia, España y Portugal.
```

**Descripción completa**
```
FuelRadar te ayuda a encontrar la gasolinera más barata cerca de ti en Francia, España y Portugal — a partir de datos públicos oficiales, gratis y sin anuncios.

FUNCIONES
• Mapa interactivo con el precio mostrado sobre cada gasolinera
• Búsqueda por dirección o por ubicación, con radio ajustable
• Lista ordenada por precio o distancia, filtrable por marca y 24/7
• Detalle de la gasolinera: todos los combustibles, tendencia de 7 días, servicios, cómo llegar
• Tendencias de precios por país (30 días, 90 días, 1 año) con gráfico interactivo
• Favoritos para tener tus gasolineras habituales a un toque
• Todos los combustibles: Gasóleo, SP95, SP95-E10, SP98, E85, GLP
• Aviso de «datos antiguos» cuando un precio no se ha actualizado recientemente
• Disponible en francés, inglés, español y portugués

FUENTES DE DATOS
Los precios provienen de datos públicos oficiales: prix-carburants.gouv.fr (Francia), Ministerio para la Transición Ecológica (España) y DGEG (Portugal). Las marcas se enriquecen mediante OpenStreetMap.

PRIVACIDAD
No se necesita cuenta. Tu ubicación solo se usa para centrar el mapa en tu dispositivo; nunca se almacena ni se comparte.
```

---

## Português (pt-PT)

**Título**
```
FuelRadar
```

**Descrição curta** (67)
```
Preços dos combustíveis em tempo real em França, Espanha e Portugal.
```

**Descrição completa**
```
O FuelRadar ajuda-o a encontrar o posto mais barato perto de si em França, Espanha e Portugal — a partir de dados públicos oficiais, grátis e sem publicidade.

FUNCIONALIDADES
• Mapa interativo com o preço apresentado sobre cada posto
• Pesquisa por morada ou por localização, com raio ajustável
• Lista ordenada por preço ou distância, filtrável por marca e 24/7
• Detalhe do posto: todos os combustíveis, tendência de 7 dias, serviços, direções
• Tendências de preços por país (30 dias, 90 dias, 1 ano) com gráfico interativo
• Favoritos para ter os seus postos habituais à distância de um toque
• Todos os combustíveis: Gasóleo, SP95, SP95-E10, SP98, E85, GPL
• Aviso de «dados antigos» quando um preço não é atualizado há algum tempo
• Disponível em francês, inglês, espanhol e português

FONTES DE DADOS
Os preços provêm de dados públicos oficiais: prix-carburants.gouv.fr (França), Ministerio para la Transición Ecológica (Espanha) e DGEG (Portugal). As marcas são enriquecidas via OpenStreetMap.

PRIVACIDADE
Não é necessária conta. A sua localização é usada apenas para centrar o mapa no seu dispositivo; nunca é armazenada nem partilhada.
```

---

## Graphic assets still needed (your side)

- **App icon** 512×512 PNG (export from the adaptive icon / `apps/web/public/icon.svg`).
- **Feature graphic** 1024×500 PNG (banner).
- **Phone screenshots** ≥ 2 (min 320px, 16:9 or 9:16): Map, Stations list, Station detail, Trends are good candidates.
- Optional: 7-inch / 10-inch tablet screenshots.

## Signing / upload reminder

The generated `.aab` is currently **debug-signed** (fallback). Before uploading:
1. Create `apps/android/keystore.properties` from `keystore.properties.example`.
2. Re-run `./gradlew :app:bundleRelease` — it will sign with your upload key.
3. Prefer Play App Signing (Google manages the app signing key; you keep the upload key).
