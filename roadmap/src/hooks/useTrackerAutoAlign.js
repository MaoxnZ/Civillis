import { useEffect, useRef } from "react";

export function useTrackerAutoAlign({ view, timelineZoom, todayLineLeft, rowsLength, unitCount, timelineRef, timelineScrollLeftRef, scrollTimelineToCurrent }) {
  const pendingTrackerAutoAlignRef = useRef(true);
  const prevTimelineZoomRef = useRef(timelineZoom);

  useEffect(() => {
    if (view !== "tracker") return;
    if (!pendingTrackerAutoAlignRef.current) return;
    if (!todayLineLeft) return;
    let raf1 = 0;
    let raf2 = 0;
    raf1 = window.requestAnimationFrame(() => {
      raf2 = window.requestAnimationFrame(() => {
        scrollTimelineToCurrent();
        pendingTrackerAutoAlignRef.current = false;
      });
    });
    return () => {
      if (raf1) window.cancelAnimationFrame(raf1);
      if (raf2) window.cancelAnimationFrame(raf2);
    };
  }, [view, todayLineLeft, timelineZoom, rowsLength, unitCount, scrollTimelineToCurrent]);

  useEffect(() => {
    if (view !== "tracker") return;
    if (pendingTrackerAutoAlignRef.current) return;
    let raf = 0;
    raf = window.requestAnimationFrame(() => {
      const el = timelineRef.current;
      if (!el) return;
      const max = Math.max(0, el.scrollWidth - el.clientWidth);
      el.scrollLeft = Math.max(0, Math.min(max, timelineScrollLeftRef.current));
    });
    return () => {
      if (raf) window.cancelAnimationFrame(raf);
    };
  }, [view, timelineZoom, rowsLength, unitCount, timelineRef, timelineScrollLeftRef]);

  useEffect(() => {
    const prevZoom = prevTimelineZoomRef.current;
    prevTimelineZoomRef.current = timelineZoom;
    if (view !== "tracker") return;
    if (prevZoom === timelineZoom) return;
    if (!todayLineLeft) return;
    let raf1 = 0;
    let raf2 = 0;
    raf1 = window.requestAnimationFrame(() => {
      raf2 = window.requestAnimationFrame(() => {
        scrollTimelineToCurrent();
      });
    });
    return () => {
      if (raf1) window.cancelAnimationFrame(raf1);
      if (raf2) window.cancelAnimationFrame(raf2);
    };
  }, [view, timelineZoom, todayLineLeft, rowsLength, unitCount, scrollTimelineToCurrent]);
}
