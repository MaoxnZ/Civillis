# Roadmap data (`src/data/`)

The site reads **four JSON files** (see `roadmap-adapter.js`). **`schemaVersion` must stay `2`** in `roadmap-metadata.json` or the app will throw.

Allowed status strings and workflow notes are duplicated for humans in **`roadmap-metadata.json` → `authoringGuide`**; the adapter enforces a subset at runtime.

---

### `roadmap-metadata.json`

Site title, subtitle, `lastUpdated`, and **`timeline.range`** (`start` / `end` as `YYYY-MM-DD`). The timeline view builds a **month axis** from that range; feature/tracker bars must fall inside it or they clip oddly.

---

### `roadmap-features.json`

**Array of features.** Each object is the catalog entry for one capability.

| Field | Role |
|--------|------|
| `id` | Stable key, e.g. `f-…`. Referenced everywhere else. |
| `name` | Short title in the UI. |
| `status` | **`Planned`**, **`Developing`**, or **`Committed`** (invalid values error at load). |
| `progress` | 0–100. |
| `details` or `description` | Long text; both are treated as the same body copy. |
| `items` | Bullet strings for the feature modal. |
| `images` | URLs or `__PLACEHOLDER__` (resolved in the adapter). |
| `links` | Optional `{ "label", "url" }[]`. |

When a release is **`Released`**, any linked feature with status **`Committed`** is shown as **Released** on that release card (see adapter).

---

### `roadmap-versions.json`

**Array of streams** (e.g. `1.0-alpha`, `1.2-release`). Each stream has optional `stream`, `phase`, `summaryTitle`, and **`releases`**.

Each **release** object:

| Field | Role |
|--------|------|
| `id` | Unique release id. |
| `version` | Semver string for ordering. |
| `phase` | e.g. `alpha` / `beta` / `release` (affects sort with same version). |
| `status` | **`Tentative`**, **`In Progress`**, **`Under Review`**, **`Released`**. |
| `theme`, `summary`, `image` | Release card copy and hero image. |
| **`featureRefs`** | List of **`roadmap-features.json` `id`s** — defines which features appear on that version card. |

**Release view** is entirely driven by this file + the feature catalog.

---

### `roadmap-tracker.json`

**Object with `pipelines`:** each row is one horizontal track on the tracker.

| Field | Role |
|--------|------|
| `id` | Stable pipeline id. |
| `name` | Row label. Convention: **`Domain | Subdomain`** for most rows (e.g. `Simulation | Decay`); the **`Tentative`** row is a single label with **no** `|`, and speculative items are plain **`children`**. |
| `description` | Row blurb. |
| `status` | Shown on the pipeline; use **`Tentative`** for fantasy-only tracks. |
| `startDate` / `endDate` | `YYYY-MM-DD` for the row bar (also used with the month axis). |
| **`children`** | Scheduled instances: `featureRef` (**must** match a feature `id`), `startDate`, `endDate`, `archived` (dims old segments in the UI), optional `id`. |

Tracker rows **do not** define new features — they only **schedule** existing `f-…` ids. Add or edit the feature in **`roadmap-features.json`** first, then reference it here and in **`featureRefs`** on a release when you ship it.

---

### Local run

From repo root: `cd roadmap && npm ci && npm run dev` (or `npm install`). Production build: `npm run build`. CI copies **`dist/`** into the docs site; do not commit `node_modules/` or `dist/`.
