export function formatMonthLabel(monthKey) {
  const [yearStr, monthStr] = monthKey.split("-");
  const date = new Date(Date.UTC(Number(yearStr), Number(monthStr) - 1, 1));
  return date.toLocaleString("en-US", { month: "short" });
}

export function topVersionKey(windowLabel) {
  const match = windowLabel.match(/(\d+)\.(\d+)/);
  if (!match) return windowLabel;
  return `${match[1]}.${match[2]}`;
}

function parseVersion(versionStr) {
  return String(versionStr || "")
    .split(".")
    .map((part) => Number(part))
    .filter((n) => Number.isFinite(n));
}

export function compareVersionsAsc(a, b) {
  const va = parseVersion(a);
  const vb = parseVersion(b);
  const len = Math.max(va.length, vb.length);
  for (let i = 0; i < len; i += 1) {
    const diff = (va[i] ?? 0) - (vb[i] ?? 0);
    if (diff !== 0) return diff;
  }
  return 0;
}

export function groupSummaryTitle(group) {
  return group.summaryTitle || group.label || group.key;
}

export function scheduleLabel(schedule, months) {
  if (!schedule) return "TBD";
  const start = Math.max(0, Math.min(months.length - 1, schedule.start ?? 0));
  const end = Math.max(start, Math.min(months.length - 1, schedule.end ?? start));
  return `${months[start]} - ${months[end]}`;
}

export function releasedInLabel(releasedIn) {
  if (!releasedIn?.window) return "";
  return `Released in ${releasedIn.window}`;
}

export function entryCountLabel(count) {
  const n = Number.isFinite(count) ? count : 0;
  return `${n} ${n === 1 ? "entry" : "entries"}`;
}

export function parseDateYmdUtc(dateStr) {
  const m = String(dateStr || "").match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!m) return null;
  const y = Number(m[1]);
  const mo = Number(m[2]);
  const d = Number(m[3]);
  if (!Number.isFinite(y) || !Number.isFinite(mo) || !Number.isFinite(d)) return null;
  const date = new Date(Date.UTC(y, mo - 1, d));
  if (date.getUTCFullYear() !== y || date.getUTCMonth() !== mo - 1 || date.getUTCDate() !== d) return null;
  return date;
}

export function addUtcDays(date, days) {
  return new Date(date.getTime() + days * 86400000);
}

export function dateToTimelineUnitPosition(date, months, unitCount, timelineZoom, weekLayout) {
  if (!(date instanceof Date) || unitCount <= 0 || months.length === 0) return null;
  const first = months[0];
  const m = String(first).match(/^(\d{4})-(\d{2})$/);
  if (!m) return null;
  const baseYear = Number(m[1]);
  const baseMonth = Number(m[2]);
  const dateYear = date.getUTCFullYear();
  const dateMonth = date.getUTCMonth() + 1;
  const monthOffset = (dateYear - baseYear) * 12 + (dateMonth - baseMonth);
  if (monthOffset < 0) return 0;
  if (monthOffset >= months.length) return unitCount;
  const daysInMonth = new Date(Date.UTC(dateYear, dateMonth, 0)).getUTCDate();
  const dayRatio = Math.max(0, Math.min(1, (date.getUTCDate() - 1) / Math.max(1, daysInMonth)));
  if (timelineZoom !== "week") {
    return monthOffset + dayRatio;
  }
  const monthStart = weekLayout?.monthStartOffsets?.[monthOffset] ?? monthOffset * 4;
  const weeksInMonth = weekLayout?.monthWeekCounts?.[monthOffset] ?? 4;
  return monthStart + dayRatio * weeksInMonth;
}

export function durationDaysLabel(startDate, endDate, fallbackSchedule, months) {
  const start = String(startDate || "");
  const end = String(endDate || "");
  const fullDate = /^\d{4}-\d{2}-\d{2}$/;
  if (fullDate.test(start) && fullDate.test(end)) {
    const startTs = Date.parse(`${start}T00:00:00Z`);
    const endTs = Date.parse(`${end}T00:00:00Z`);
    if (Number.isFinite(startTs) && Number.isFinite(endTs) && endTs >= startTs) {
      const dayCount = Math.floor((endTs - startTs) / 86400000) + 1;
      return `${start} - ${end} (${dayCount}d)`;
    }
    return `${start} - ${end}`;
  }
  return scheduleLabel(fallbackSchedule, months);
}

export function visibleTrackerChildren(top, hideReleasedFeatures) {
  const topChildren = top?.children ?? [];
  if (!hideReleasedFeatures) return topChildren;
  return topChildren.filter((child) => String(child.status).toLowerCase() !== "released");
}

export function buildTrackRows(tracks, expandedTracks, hideReleasedFeatures) {
  const rows = [];
  tracks.forEach((top) => {
    const visibleChildren = visibleTrackerChildren(top, hideReleasedFeatures);
    if (visibleChildren.length === 0) return;
    rows.push({ ...top, depth: 0, hasChildren: visibleChildren.length > 0, isOpen: !!expandedTracks[top.id] });
    if (expandedTracks[top.id] && visibleChildren.length > 0) {
      visibleChildren.forEach((child) => {
        rows.push({ ...child, depth: 1, hasChildren: false, parentId: top.id, subItems: child.children ?? [] });
      });
    }
  });
  return rows;
}

export function pickLatestReleasedCard(cards) {
  const releasedReleasePhase = (cards ?? []).filter(
    (card) => String(card.status).toLowerCase() === "released" && String(card.phase).toLowerCase() === "release"
  );
  const pool = releasedReleasePhase.length > 0 ? releasedReleasePhase : (cards ?? []).filter((card) => String(card.status).toLowerCase() === "released");
  if (pool.length === 0) return null;
  return [...pool].sort((a, b) => compareVersionsAsc(a.version, b.version))[pool.length - 1];
}
