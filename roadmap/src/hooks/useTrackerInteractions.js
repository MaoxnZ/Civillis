export function useTrackerInteractions({
  topTrackIds,
  setExpandedTracks,
  timelineRef,
  dragRef,
  setIsTimelineDragging,
  timelineScrollLeftRef,
  todayLineLeft,
  timelineHeaderLeftRef,
  unitCount,
  zoomCellWidth,
}) {
  function setAllTopTracks(open) {
    const patch = {};
    topTrackIds.forEach((id) => (patch[id] = open));
    setExpandedTracks((prev) => ({ ...prev, ...patch }));
  }

  function handleTimelineMouseDown(e) {
    if (e.button !== 0) return;
    const target = e.target;
    if (target instanceof HTMLElement && target.closest("button,a,input,textarea")) return;
    const el = timelineRef.current;
    if (!el) return;
    dragRef.current.active = true;
    dragRef.current.startX = e.clientX;
    dragRef.current.startScrollLeft = el.scrollLeft;
    setIsTimelineDragging(true);
  }

  function handleTimelineMouseMove(e) {
    if (!dragRef.current.active) return;
    const el = timelineRef.current;
    if (!el) return;
    const dx = e.clientX - dragRef.current.startX;
    el.scrollLeft = dragRef.current.startScrollLeft - dx;
    timelineScrollLeftRef.current = el.scrollLeft;
  }

  function handleTimelineMouseUp() {
    dragRef.current.active = false;
    setIsTimelineDragging(false);
  }

  function scrollTimelineToProgress(progress, anchorRatio = 0) {
    const el = timelineRef.current;
    if (!el) return;
    const max = Math.max(0, el.scrollWidth - el.clientWidth);
    const clamped = Math.max(0, Math.min(1, progress));
    const anchorPx = Math.max(0, Math.min(el.clientWidth * 0.45, el.clientWidth * anchorRatio));
    const next = clamped * max - anchorPx;
    el.scrollLeft = Math.max(0, Math.min(max, next));
    timelineScrollLeftRef.current = el.scrollLeft;
  }

  function scrollTimelineToCurrent() {
    if (!todayLineLeft) return;
    const el = timelineRef.current;
    if (!el) return;
    const max = Math.max(0, el.scrollWidth - el.clientWidth);
    const pct = Number(todayLineLeft.replace("%", "")) / 100;
    if (!Number.isFinite(pct)) return;
    const leftHeaderEl = timelineHeaderLeftRef.current;
    const leftWidth = leftHeaderEl instanceof HTMLElement ? leftHeaderEl.offsetWidth : 0;
    const rightVisibleWidth = Math.max(0, el.clientWidth - leftWidth);
    const rightContentWidth = Math.max(1, unitCount * zoomCellWidth);

    // Keep current line near left edge (10%) in visible timeline panel.
    const lineContentX = leftWidth + pct * rightContentWidth;
    const targetViewportX = leftWidth + rightVisibleWidth * 0.1;
    const next = lineContentX - targetViewportX;
    el.scrollLeft = Math.max(0, Math.min(max, next));
    timelineScrollLeftRef.current = el.scrollLeft;
  }

  function nudgeTimeline(direction) {
    const el = timelineRef.current;
    if (!el) return;
    const delta = Math.max(120, Math.round(el.clientWidth * 0.35));
    el.scrollBy({ left: direction * delta, behavior: "smooth" });
  }

  return {
    setAllTopTracks,
    handleTimelineMouseDown,
    handleTimelineMouseMove,
    handleTimelineMouseUp,
    scrollTimelineToProgress,
    scrollTimelineToCurrent,
    nudgeTimeline,
  };
}
