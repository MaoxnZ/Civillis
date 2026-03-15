import { useEffect, useState } from "react";

export function useTrackerPinnedRows({
  view,
  trackerControlsTop,
  trackerControlsPinAnchorRef,
  trackerControlsRef,
  timelineHeaderPinAnchorRef,
  timelineShellRef,
  timelineStickyHeaderRef,
}) {
  const [trackerControlsFixed, setTrackerControlsFixed] = useState({ active: false, left: 0, width: 0, height: 0 });
  const [trackerHeaderFixed, setTrackerHeaderFixed] = useState({ active: false, left: 0, width: 0, height: 0 });

  useEffect(() => {
    if (view !== "tracker") {
      setTrackerControlsFixed((prev) => (prev.active ? { active: false, left: 0, width: 0, height: 0 } : prev));
      setTrackerHeaderFixed((prev) => (prev.active ? { active: false, left: 0, width: 0, height: 0 } : prev));
      return;
    }
    function syncTrackerPinnedRows() {
      const controlsAnchor = trackerControlsPinAnchorRef.current;
      const controls = trackerControlsRef.current;
      const headerAnchor = timelineHeaderPinAnchorRef.current;
      const shell = timelineShellRef.current;
      const header = timelineStickyHeaderRef.current;
      if (
        !(controlsAnchor instanceof HTMLElement) ||
        !(controls instanceof HTMLElement) ||
        !(headerAnchor instanceof HTMLElement) ||
        !(shell instanceof HTMLElement) ||
        !(header instanceof HTMLElement)
      ) {
        return;
      }
      const controlsAnchorRect = controlsAnchor.getBoundingClientRect();
      const controlsRect = controls.getBoundingClientRect();
      const headerAnchorRect = headerAnchor.getBoundingClientRect();
      const shellRect = shell.getBoundingClientRect();
      const controlsHeight = controls.offsetHeight || 0;
      const headerHeight = header.offsetHeight || 0;
      const shouldPinControls = controlsAnchorRect.top <= trackerControlsTop && shellRect.bottom > trackerControlsTop + controlsHeight;
      const headerPinTop = shouldPinControls ? trackerControlsTop + controlsHeight + 8 : 0;
      const shouldPinHeader = headerAnchorRect.top <= headerPinTop && shellRect.bottom > headerPinTop + headerHeight;

      setTrackerControlsFixed((prev) => {
        if (!shouldPinControls) {
          if (!prev.active && prev.height === controlsHeight) return prev;
          return { active: false, left: 0, width: 0, height: controlsHeight };
        }
        const next = {
          active: true,
          left: Math.round(controlsRect.left),
          width: Math.round(controlsRect.width),
          height: controlsHeight,
        };
        if (prev.active && prev.left === next.left && prev.width === next.width && prev.height === next.height) return prev;
        return next;
      });

      setTrackerHeaderFixed((prev) => {
        if (!shouldPinHeader) {
          if (!prev.active && prev.height === headerHeight) return prev;
          return { active: false, left: 0, width: 0, height: headerHeight };
        }
        const next = {
          active: true,
          left: Math.round(shellRect.left),
          width: Math.round(shellRect.width),
          height: headerHeight,
        };
        if (prev.active && prev.left === next.left && prev.width === next.width && prev.height === next.height) return prev;
        return next;
      });
    }
    syncTrackerPinnedRows();
    window.addEventListener("scroll", syncTrackerPinnedRows, { passive: true });
    window.addEventListener("resize", syncTrackerPinnedRows);
    return () => {
      window.removeEventListener("scroll", syncTrackerPinnedRows);
      window.removeEventListener("resize", syncTrackerPinnedRows);
    };
  }, [
    view,
    trackerControlsTop,
    trackerControlsPinAnchorRef,
    trackerControlsRef,
    timelineHeaderPinAnchorRef,
    timelineShellRef,
    timelineStickyHeaderRef,
  ]);

  return {
    trackerControlsFixed,
    trackerHeaderFixed,
  };
}
