import roadmapMetadata from "./roadmap-metadata.json";
import roadmapFeatures from "./roadmap-features.json";
import roadmapVersions from "./roadmap-versions.json";
import roadmapTracker from "./roadmap-tracker.json";
import { CIVILLIS_WATERMARK_PLACEHOLDER, IMAGE_REF_PLACEHOLDER } from "../constants/image-placeholders";

function parseDateUtc(dateStr) {
  const [y, m, d] = String(dateStr || "").split("-").map(Number);
  if (!Number.isFinite(y) || !Number.isFinite(m) || !Number.isFinite(d)) return null;
  return new Date(Date.UTC(y, m - 1, d));
}

function monthKeyFromDate(date) {
  const y = date.getUTCFullYear();
  const m = `${date.getUTCMonth() + 1}`.padStart(2, "0");
  return `${y}-${m}`;
}

function buildMonthAxis(range) {
  const start = parseDateUtc(range?.start);
  const end = parseDateUtc(range?.end);
  if (!start || !end || start > end) return [];
  const out = [];
  let cur = new Date(Date.UTC(start.getUTCFullYear(), start.getUTCMonth(), 1));
  const endMonth = new Date(Date.UTC(end.getUTCFullYear(), end.getUTCMonth(), 1));
  while (cur <= endMonth) {
    out.push(monthKeyFromDate(cur));
    cur = new Date(Date.UTC(cur.getUTCFullYear(), cur.getUTCMonth() + 1, 1));
  }
  return out;
}

function resolveImageRef(imageRef) {
  const normalized = String(imageRef || "").trim();
  if (!normalized) return "";
  if (normalized === IMAGE_REF_PLACEHOLDER) return CIVILLIS_WATERMARK_PLACEHOLDER;
  return normalized;
}

function dedupeImages(images) {
  const merged = [...(Array.isArray(images) ? images : [])].filter(Boolean);
  const seen = new Set();
  const out = [];
  merged.forEach((img) => {
    const normalized = resolveImageRef(img);
    if (!normalized || seen.has(normalized)) return;
    seen.add(normalized);
    out.push(normalized);
  });
  return out;
}

function scheduleFromDates(startDate, endDate, monthAxis) {
  if (!startDate || !endDate || monthAxis.length === 0) return null;
  const start = parseDateUtc(startDate);
  const end = parseDateUtc(endDate);
  if (!start || !end) return null;
  const startKey = monthKeyFromDate(start);
  const endKey = monthKeyFromDate(end);
  const startIdx = monthAxis.indexOf(startKey);
  const endIdx = monthAxis.indexOf(endKey);
  if (startIdx < 0 && endIdx < 0) return null;
  const s = Math.max(0, startIdx < 0 ? 0 : startIdx);
  const e = Math.min(monthAxis.length - 1, endIdx < 0 ? monthAxis.length - 1 : endIdx);
  return { start: Math.min(s, e), end: Math.max(s, e) };
}

function asList(value) {
  return Array.isArray(value) ? value : [];
}

function parseVersion(versionStr) {
  return String(versionStr || "")
    .split(".")
    .map((part) => Number(part))
    .filter((n) => Number.isFinite(n));
}

function compareVersionsAsc(a, b) {
  const va = parseVersion(a);
  const vb = parseVersion(b);
  const len = Math.max(va.length, vb.length);
  for (let i = 0; i < len; i += 1) {
    const diff = (va[i] ?? 0) - (vb[i] ?? 0);
    if (diff !== 0) return diff;
  }
  return 0;
}

const FEATURE_STATUS_SET = new Set(["planned", "developing", "committed"]);
const RELEASE_STATUS_SET = new Set(["tentative", "in progress", "under review", "released"]);

function normalizeFeatureStatus(rawStatus, featureId) {
  const normalized = String(rawStatus ?? "").trim().toLowerCase();
  if (!FEATURE_STATUS_SET.has(normalized)) {
    throw new Error(`Invalid feature status "${rawStatus}" for ${featureId}.`);
  }
  if (normalized === "planned") return "Planned";
  if (normalized === "developing") return "Developing";
  return "Committed";
}

function normalizeReleaseStatus(rawStatus, releaseId) {
  const normalized = String(rawStatus ?? "").trim().toLowerCase();
  if (!RELEASE_STATUS_SET.has(normalized)) {
    throw new Error(`Invalid release status "${rawStatus}" for ${releaseId}.`);
  }
  if (normalized === "tentative") return "Tentative";
  if (normalized === "in progress") return "In Progress";
  if (normalized === "under review") return "Under Review";
  return "Released";
}

function byId(list) {
  const map = new Map();
  list.forEach((item) => {
    if (item?.id) map.set(item.id, item);
  });
  return map;
}

function mapFeature(feature) {
  const images = dedupeImages(feature?.images);
  const primaryImage = images[0] ?? "";
  const description = feature.description ?? feature.details ?? "";
  return {
    id: feature.id,
    name: feature.name,
    status: normalizeFeatureStatus(feature.status, feature.id),
    progress: Number.isFinite(feature.progress) ? feature.progress : 0,
    items: asList(feature.items),
    details: description,
    description,
    image: primaryImage,
    images,
    related: asList(feature.related),
    links: asList(feature.links),
  };
}

function mapFeatureStatusForRelease(releaseStatus, featureStatus) {
  const release = String(releaseStatus ?? "").toLowerCase();
  const feature = String(featureStatus ?? "").toLowerCase();
  if (release === "released" && feature === "committed") return "Released";
  return featureStatus ?? "Planned";
}

function mapReleaseCards(canonical, featureMap) {
  return asList(canonical).flatMap((stream) =>
    asList(stream.releases).map((release) => {
      const features = asList(release.featureRefs)
        .map((id) => featureMap.get(id))
        .filter(Boolean)
        .map((feature) => ({
          ...feature,
          status: mapFeatureStatusForRelease(release.status, feature.status),
        }));
      return {
        id: release.id,
        version: release.version,
        phase: (stream.phase ?? release.phase ?? "release").toLowerCase(),
        stream: stream.stream,
        groupSummaryTitle: stream.summaryTitle ?? "",
        window: `v${release.version}-${(release.phase ?? "release").toLowerCase()}`,
        status: normalizeReleaseStatus(release.status, release.id),
        theme: release.theme ?? "",
        summary: release.summary ?? "",
        image: resolveImageRef(release.image) || features[0]?.image || "",
        features,
      };
    })
  );
}

function releasePhaseRank(phase) {
  const key = String(phase || "").toLowerCase();
  if (key === "alpha") return 0;
  if (key === "beta") return 1;
  if (key === "release") return 2;
  return 99;
}

function compareReleasePointsAsc(a, b) {
  const versionCmp = compareVersionsAsc(a?.version, b?.version);
  if (versionCmp !== 0) return versionCmp;
  return releasePhaseRank(a?.phase) - releasePhaseRank(b?.phase);
}

function buildFeatureReleasedInMap(versionsCanonical) {
  const releasedPoints = [];
  asList(versionsCanonical).forEach((stream) => {
    const streamPhase = (stream.phase ?? "release").toLowerCase();
    asList(stream.releases).forEach((release) => {
      const status = normalizeReleaseStatus(release.status, release.id);
      if (status !== "Released") return;
      releasedPoints.push({
        id: release.id,
        version: release.version,
        phase: (release.phase ?? streamPhase ?? "release").toLowerCase(),
        window: `v${release.version}-${(release.phase ?? streamPhase ?? "release").toLowerCase()}`,
        featureRefs: asList(release.featureRefs),
      });
    });
  });
  releasedPoints.sort(compareReleasePointsAsc);
  const map = new Map();
  releasedPoints.forEach((point) => {
    point.featureRefs.forEach((featureId) => {
      if (!featureId || map.has(featureId)) return;
      map.set(featureId, {
        id: point.id,
        version: point.version,
        phase: point.phase,
        window: point.window,
      });
    });
  });
  return map;
}

function mapTracks(canonical, featureMap, monthAxis, releasedFeatureIds, releasedInByFeature) {
  return asList(canonical?.pipelines).map((pipeline) => {
    const children = asList(pipeline.children).map((child, index) => {
      const feature = featureMap.get(child.featureRef);
      const schedule = scheduleFromDates(child.startDate, child.endDate, monthAxis);
      const releasedIn = child?.featureRef ? releasedInByFeature.get(child.featureRef) ?? null : null;
      if (!feature) {
        return {
          id: child.id ?? `${pipeline.id}::missing::${index}`,
          name: child.featureRef ?? "Unknown feature",
          status: "Planned",
          progress: 0,
          schedule,
          startDate: child.startDate ?? null,
          endDate: child.endDate ?? null,
          description: "",
          archived: !!child.archived,
          children: [],
          related: [],
          releasedIn,
        };
      }
      return {
        id: child.id ?? `${pipeline.id}::${feature.id}::${index}`,
        name: feature.name,
        status: releasedFeatureIds.has(feature.id) ? "Released" : feature.status,
        progress: Number.isFinite(feature.progress) ? feature.progress : 0,
        schedule,
        startDate: child.startDate ?? null,
        endDate: child.endDate ?? null,
        description: feature.description ?? "",
        archived: !!child.archived,
        children: [],
        related: feature.links?.map((link) => link.label).filter(Boolean) ?? [],
        releasedIn,
      };
    });
    return {
      id: pipeline.id,
      name: pipeline.name,
      description: pipeline.description ?? "",
      status: pipeline.status ?? "Planned",
      progress: Number.isFinite(pipeline.progress) ? pipeline.progress : 0,
      schedule: scheduleFromDates(pipeline.startDate, pipeline.endDate, monthAxis),
      startDate: pipeline.startDate ?? null,
      endDate: pipeline.endDate ?? null,
      children,
    };
  });
}

export function getRoadmapViewModel() {
  if (roadmapMetadata?.schemaVersion !== 2) {
    throw new Error("Unsupported roadmap schemaVersion; expected 2.");
  }
  const monthAxis = buildMonthAxis(roadmapMetadata.timeline?.range);
  const featureMap = byId(asList(roadmapFeatures).map(mapFeature));
  const releaseCards = mapReleaseCards(roadmapVersions, featureMap);
  const releasedInByFeature = buildFeatureReleasedInMap(roadmapVersions);
  const releasedFeatureIds = new Set();
  releaseCards.forEach((card) => {
    if (String(card.status).toLowerCase() !== "released") return;
    asList(card.features).forEach((feature) => {
      if (feature?.id) releasedFeatureIds.add(feature.id);
    });
  });
  const tracks = mapTracks(roadmapTracker, featureMap, monthAxis, releasedFeatureIds, releasedInByFeature);
  return {
    title: roadmapMetadata.title ?? "Roadmap",
    subtitle: roadmapMetadata.subtitle ?? "",
    lastUpdated: roadmapMetadata.lastUpdated ?? "",
    timelineMonths: monthAxis,
    releaseCards,
    tracks,
  };
}
