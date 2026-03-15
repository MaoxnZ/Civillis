import { useMemo } from "react";
import {
  addUtcDays,
  buildTrackRows,
  dateToTimelineUnitPosition,
  formatMonthLabel,
  parseDateYmdUtc,
  visibleTrackerChildren,
} from "../utils/roadmap-utils";

export function useTrackerViewModel({ roadmapData, expandedTracks, hideReleasedFeatures, timelineZoom, trackerControlsFixed, trackerHeaderFixed }) {
  const tracks = useMemo(() => roadmapData.tracks ?? [], [roadmapData]);
  const months = useMemo(() => roadmapData.timelineMonths ?? [], [roadmapData]);

  const weekLayout = useMemo(() => {
    if (timelineZoom !== "week") return null;
    const monthWeekCounts = [];
    const monthStartOffsets = [];
    const quarterGroups = [];
    const units = [];
    let offset = 0;
    let previousQuarterLabel = null;
    months.forEach((m, monthIndex) => {
      const [yearStr, monthStr] = m.split("-");
      const monthNum = Number(monthStr);
      const quarter = Math.floor((monthNum - 1) / 3) + 1;
      const quarterLabel = `Q${quarter} ${yearStr}`;
      const monthInQuarter = (monthNum - 1) % 3;
      const weeksInMonth = monthInQuarter === 2 ? 5 : 4;
      monthWeekCounts.push(weeksInMonth);
      monthStartOffsets.push(offset);
      const lastQuarter = quarterGroups[quarterGroups.length - 1];
      if (lastQuarter && lastQuarter.label === quarterLabel) lastQuarter.count += weeksInMonth;
      else quarterGroups.push({ label: quarterLabel, count: weeksInMonth });
      const isQuarterBoundary = previousQuarterLabel !== null && previousQuarterLabel !== quarterLabel;
      for (let i = 0; i < weeksInMonth; i += 1) {
        const weekInQuarter = monthInQuarter * 4 + i + 1;
        units.push({
          key: `${m}-w${i + 1}`,
          label: `W${weekInQuarter}`,
          monthIndex,
          weekInMonth: i,
          quarterLabel,
          quarterBoundary: isQuarterBoundary && i === 0,
        });
      }
      previousQuarterLabel = quarterLabel;
      offset += weeksInMonth;
    });
    return { units, monthWeekCounts, monthStartOffsets, quarterGroups };
  }, [months, timelineZoom]);

  const rows = useMemo(() => buildTrackRows(tracks, expandedTracks, hideReleasedFeatures), [tracks, expandedTracks, hideReleasedFeatures]);
  const topTrackIds = useMemo(
    () => tracks.filter((t) => visibleTrackerChildren(t, hideReleasedFeatures).length > 0).map((t) => t.id),
    [tracks, hideReleasedFeatures]
  );
  const areAllTopTracksExpanded = useMemo(() => topTrackIds.length > 0 && topTrackIds.every((id) => !!expandedTracks[id]), [topTrackIds, expandedTracks]);
  const areAllTopTracksCollapsed = useMemo(() => topTrackIds.length > 0 && topTrackIds.every((id) => !expandedTracks[id]), [topTrackIds, expandedTracks]);

  const timelineUnits = useMemo(() => {
    if (timelineZoom !== "week") {
      let prevQuarterLabel = null;
      return months.map((m, idx) => {
        const [yearStr, monthStr] = m.split("-");
        const quarter = Math.floor((Number(monthStr) - 1) / 3) + 1;
        const quarterLabel = `Q${quarter} ${yearStr}`;
        const quarterBoundary = prevQuarterLabel !== null && prevQuarterLabel !== quarterLabel;
        prevQuarterLabel = quarterLabel;
        return { key: m, label: formatMonthLabel(m), monthIndex: idx, quarterBoundary };
      });
    }
    return weekLayout?.units ?? [];
  }, [months, timelineZoom, weekLayout]);

  const unitCount = Math.max(1, timelineUnits.length);
  const zoomCellWidth = timelineZoom === "month" ? 170 : 84;

  const quarterGroups = useMemo(() => {
    if (timelineZoom === "week") {
      return weekLayout?.quarterGroups ?? [];
    }
    const groups = [];
    months.forEach((m) => {
      const [yearStr, monthStr] = m.split("-");
      const quarter = Math.floor((Number(monthStr) - 1) / 3) + 1;
      const label = `Q${quarter} ${yearStr}`;
      const last = groups[groups.length - 1];
      if (last && last.label === label) last.count += 1;
      else groups.push({ label, count: 1 });
    });
    return groups;
  }, [months, timelineZoom, weekLayout]);

  const trackerPrelaunchRatio = useMemo(() => {
    const firstFormalMonth = months.findIndex((m) => m >= "2026-01");
    if (firstFormalMonth <= 0) return 0;
    if (timelineZoom !== "week") return firstFormalMonth / unitCount;
    const weekOffset = weekLayout?.monthStartOffsets?.[firstFormalMonth] ?? 0;
    return Math.max(0, Math.min(1, weekOffset / unitCount));
  }, [months, timelineZoom, unitCount, weekLayout]);

  const trackerControlsTop = 20;
  const trackerHeaderTop = trackerControlsFixed.active ? trackerControlsTop + trackerControlsFixed.height + 8 : 0;
  const trackerPinMask = useMemo(() => {
    const active = trackerControlsFixed.active || trackerHeaderFixed.active;
    if (!active) return null;
    const left = trackerHeaderFixed.active ? trackerHeaderFixed.left : trackerControlsFixed.left;
    const width = trackerHeaderFixed.active ? trackerHeaderFixed.width : trackerControlsFixed.width;
    if (!Number.isFinite(left) || !Number.isFinite(width) || width <= 0) return null;
    const height = trackerHeaderFixed.active ? trackerHeaderTop : trackerControlsTop + trackerControlsFixed.height;
    if (!Number.isFinite(height) || height <= 0) return null;
    return {
      left: Math.max(0, left),
      width: Math.max(0, width),
      height: Math.max(0, Math.floor(height)),
    };
  }, [trackerControlsFixed, trackerHeaderFixed, trackerControlsTop, trackerHeaderTop]);

  const todayLineLeft = useMemo(() => {
    const now = new Date();
    const localYmd = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
    const todayDate = parseDateYmdUtc(localYmd);
    const pos = dateToTimelineUnitPosition(todayDate, months, unitCount, timelineZoom, weekLayout);
    if (!Number.isFinite(pos)) return null;
    const clamped = Math.max(0, Math.min(unitCount, pos));
    return `${(clamped / unitCount) * 100}%`;
  }, [months, timelineZoom, unitCount, weekLayout]);

  function barStyle(row, depth) {
    if (unitCount <= 0) return { left: "0%", width: "0%" };
    const startDate = parseDateYmdUtc(row?.startDate);
    const endDate = parseDateYmdUtc(row?.endDate);
    if (startDate && endDate && endDate >= startDate) {
      const startPos = dateToTimelineUnitPosition(startDate, months, unitCount, timelineZoom, weekLayout);
      const endExclusivePos = dateToTimelineUnitPosition(addUtcDays(endDate, 1), months, unitCount, timelineZoom, weekLayout);
      if (Number.isFinite(startPos) && Number.isFinite(endExclusivePos) && endExclusivePos > startPos) {
        const left = (Math.max(0, Math.min(unitCount, startPos)) / unitCount) * 100;
        const width = ((Math.max(0, Math.min(unitCount, endExclusivePos)) - Math.max(0, Math.min(unitCount, startPos))) / unitCount) * 100;
        return { left: `${left}%`, width: `${width}%`, "--bar-h": depth === 0 ? "26px" : "18px" };
      }
    }
    const schedule = row?.schedule;
    if (!schedule) return { left: "0%", width: "0%" };
    let startRaw = schedule.start ?? 0;
    let endRaw = schedule.end ?? startRaw;
    if (timelineZoom === "week") {
      const safeStartMonth = Math.max(0, Math.min(months.length - 1, startRaw));
      const safeEndMonth = Math.max(safeStartMonth, Math.min(months.length - 1, endRaw));
      const startOffset = weekLayout?.monthStartOffsets?.[safeStartMonth] ?? safeStartMonth * 4;
      const endOffset = weekLayout?.monthStartOffsets?.[safeEndMonth] ?? safeEndMonth * 4;
      const endWeeks = weekLayout?.monthWeekCounts?.[safeEndMonth] ?? 4;
      startRaw = startOffset;
      endRaw = endOffset + endWeeks - 1;
    }
    const start = Math.max(0, Math.min(unitCount - 1, startRaw));
    const end = Math.max(start, Math.min(unitCount - 1, endRaw));
    const left = (start / unitCount) * 100;
    const width = ((end - start + 1) / unitCount) * 100;
    return { left: `${left}%`, width: `${width}%`, "--bar-h": depth === 0 ? "26px" : "18px" };
  }

  function trackFillClass(progress) {
    if (progress <= 0) return "tentative";
    if (progress >= 100) return "done";
    return "working";
  }

  return {
    tracks,
    months,
    weekLayout,
    rows,
    topTrackIds,
    areAllTopTracksExpanded,
    areAllTopTracksCollapsed,
    timelineUnits,
    unitCount,
    zoomCellWidth,
    quarterGroups,
    trackerPrelaunchRatio,
    trackerControlsTop,
    trackerHeaderTop,
    trackerPinMask,
    todayLineLeft,
    barStyle,
    trackFillClass,
  };
}
