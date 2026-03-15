import { useEffect, useMemo, useRef, useState } from "react";
import { getRoadmapViewModel } from "./data/roadmap-adapter";
import { FeatureModal } from "./components/FeatureModal";
import { ReleaseView } from "./components/ReleaseView";
import { TrackerControlRow } from "./components/TrackerControlRow";
import { TrackerTimeline } from "./components/TrackerTimeline";
import { useFeatureModalInteractions } from "./hooks/useFeatureModalInteractions";
import { useReleaseViewInteractions } from "./hooks/useReleaseViewInteractions";
import { useReleaseViewModel } from "./hooks/useReleaseViewModel";
import { useCardsGlobalGestures } from "./hooks/useCardsGlobalGestures";
import { useCardsLayoutSync } from "./hooks/useCardsLayoutSync";
import { useTrackerAutoAlign } from "./hooks/useTrackerAutoAlign";
import { useTrackerInteractions } from "./hooks/useTrackerInteractions";
import { useTrackerPinnedRows } from "./hooks/useTrackerPinnedRows";
import { useTrackerViewModel } from "./hooks/useTrackerViewModel";

const ZOOM_OPTIONS = ["month", "week"];

function readQueryState() {
  if (typeof window === "undefined") return { view: "cards", zoom: "month" };
  const p = new URLSearchParams(window.location.search);
  return {
    view: p.get("view") === "tracker" ? "tracker" : "cards",
    zoom: ZOOM_OPTIONS.includes(p.get("zoom")) ? p.get("zoom") : "month",
  };
}

export default function RoadmapPage() {
  const roadmapData = useMemo(() => getRoadmapViewModel(), []);
  const initial = useMemo(() => readQueryState(), []);
  const [view, setView] = useState(initial.view);
  const [timelineZoom, setTimelineZoom] = useState(initial.zoom);
  const [shareCopied, setShareCopied] = useState(false);
  const [showPastGroups, setShowPastGroups] = useState(false);
  const [expandedCards, setExpandedCards] = useState({});
  const [expandedGroups, setExpandedGroups] = useState({});
  const [groupCardPage, setGroupCardPage] = useState({});
  const [groupRowHeights, setGroupRowHeights] = useState({});
  const [isCardsRailDragging, setIsCardsRailDragging] = useState(false);
  const [cardsScrollProgress, setCardsScrollProgress] = useState(0);
  const [hasCardsOverflow, setHasCardsOverflow] = useState(false);
  const [expandedTracks, setExpandedTracks] = useState(() => {
    const map = {};
    (roadmapData.tracks ?? []).forEach((t) => {
      map[t.id] = true;
    });
    return map;
  });
  const [activeTrackDetailId, setActiveTrackDetailId] = useState(null);
  const [hoveredRowId, setHoveredRowId] = useState(null);
  const [isTimelineDragging, setIsTimelineDragging] = useState(false);
  const [hideReleasedFeatures, setHideReleasedFeatures] = useState(false);
  const [timelineScrollLeft, setTimelineScrollLeft] = useState(0);

  const timelineRef = useRef(null);
  const trackerControlsPinAnchorRef = useRef(null);
  const trackerControlsRef = useRef(null);
  const timelineShellRef = useRef(null);
  const timelineHeaderPinAnchorRef = useRef(null);
  const timelineStickyHeaderRef = useRef(null);
  const timelineHeaderLeftRef = useRef(null);
  const cardsRailShellRef = useRef(null);
  const cardsRailRef = useRef(null);
  const cardsDragRef = useRef({ active: false, startX: 0, startScrollLeft: 0 });
  const cardsRailScrollLeftRef = useRef(0);
  const dragRef = useRef({ active: false, startX: 0, startScrollLeft: 0 });
  const groupRowRefs = useRef({});
  const groupWheelRefs = useRef({});
  const groupRowObserversRef = useRef({});
  const isRestoringGroupRowsRef = useRef(false);
  const ignoreGroupScrollUntilRef = useRef(0);
  const initialLatestReleaseAppliedRef = useRef(false);
  const historyDividerRef = useRef(null);
  const pendingPastOpenAlignRef = useRef(false);
  const timelineScrollLeftRef = useRef(0);

  const releaseVm = useReleaseViewModel({
    roadmapData,
    showPastGroups,
    expandedGroups,
    expandedCards,
    setExpandedGroups,
  });

  const {
    expandAllHierarchy,
    collapseAllHierarchy,
    setDefaultHierarchy,
    togglePastReleases,
    toggleGroupExpanded,
    setGroupRowRef,
    syncGroupRowHeight,
    bindGroupRowObserver,
    scrollGroupToPage,
    handleGroupRowScroll,
    handleGroupRowWheel,
    toggleCardExpanded,
  } = useReleaseViewInteractions({
    targetGroups: releaseVm.targetGroups,
    showPastGroups,
    pastGroups: releaseVm.pastGroups,
    setShowPastGroups,
    setExpandedGroups,
    setExpandedCards,
    setGroupCardPage,
    groupCardPage,
    setGroupRowHeights,
    groupRowRefs,
    groupRowObserversRef,
    groupWheelRefs,
    pendingPastOpenAlignRef,
    isRestoringGroupRowsRef,
    ignoreGroupScrollUntilRef,
  });

  const { syncCardsFloatingScrollbar, handleCardsRailShellScroll } = useCardsLayoutSync({
    view,
    showPastGroups,
    pastGroups: releaseVm.pastGroups,
    activeGroups: releaseVm.activeGroups,
    groupedCards: releaseVm.groupedCards,
    expandedGroups,
    expandedCards,
    groupCardPage,
    cardsRailShellRef,
    cardsRailScrollLeftRef,
    historyDividerRef,
    pendingPastOpenAlignRef,
    isRestoringGroupRowsRef,
    ignoreGroupScrollUntilRef,
    groupRowRefs,
    groupRowObserversRef,
    syncGroupRowHeight,
    bindGroupRowObserver,
    setHasCardsOverflow,
    setCardsScrollProgress,
  });

  const trackerControlsTop = 20;
  const { trackerControlsFixed, trackerHeaderFixed } = useTrackerPinnedRows({
    view,
    trackerControlsTop,
    trackerControlsPinAnchorRef,
    trackerControlsRef,
    timelineHeaderPinAnchorRef,
    timelineShellRef,
    timelineStickyHeaderRef,
  });

  const trackerVm = useTrackerViewModel({
    roadmapData,
    expandedTracks,
    hideReleasedFeatures,
    timelineZoom,
    trackerControlsFixed,
    trackerHeaderFixed,
  });

  const {
    selectedFeature,
    modalImageIndex,
    modalImageLoaded,
    imageTransitionDir,
    modalWheelRef,
    modalImages,
    safeModalImageIndex,
    setModalImageLoaded,
    openFeatureModal,
    closeFeatureModal,
    changeModalImage,
  } = useFeatureModalInteractions();

  const { setAllTopTracks, handleTimelineMouseDown, handleTimelineMouseMove, handleTimelineMouseUp, scrollTimelineToCurrent, nudgeTimeline } =
    useTrackerInteractions({
      topTrackIds: trackerVm.topTrackIds,
      setExpandedTracks,
      timelineRef,
      dragRef,
      setIsTimelineDragging,
      timelineScrollLeftRef,
      todayLineLeft: trackerVm.todayLineLeft,
      timelineHeaderLeftRef,
      unitCount: trackerVm.unitCount,
      zoomCellWidth: trackerVm.zoomCellWidth,
    });

  useTrackerAutoAlign({
    view,
    timelineZoom,
    todayLineLeft: trackerVm.todayLineLeft,
    rowsLength: trackerVm.rows.length,
    unitCount: trackerVm.unitCount,
    timelineRef,
    timelineScrollLeftRef,
    scrollTimelineToCurrent,
  });

  useCardsGlobalGestures({
    view,
    selectedFeature,
    hasCardsOverflow,
    cardsRailShellRef,
    cardsDragRef,
    cardsRailScrollLeftRef,
    groupRowRefs,
    handleGroupRowWheel,
    setIsCardsRailDragging,
    syncCardsFloatingScrollbar,
  });

  useEffect(() => {
    if (typeof window === "undefined") return;
    const p = new URLSearchParams();
    if (view !== "cards") p.set("view", view);
    if (timelineZoom !== "month") p.set("zoom", timelineZoom);
    const q = p.toString();
    window.history.replaceState({}, "", q ? `${window.location.pathname}?${q}` : window.location.pathname);
  }, [view, timelineZoom]);

  useEffect(() => {
    if (initialLatestReleaseAppliedRef.current) return;
    const target = releaseVm.latestReleasedGroupPage;
    if (!target) return;
    ignoreGroupScrollUntilRef.current = Date.now() + 600;
    const activeKey = `active-${target.groupKey}`;
    const pastKey = `past-${target.groupKey}`;
    setGroupCardPage((prev) => {
      const next = { ...prev };
      let changed = false;
      if (next[activeKey] === undefined) {
        next[activeKey] = target.page;
        changed = true;
      }
      if (next[pastKey] === undefined) {
        next[pastKey] = target.page;
        changed = true;
      }
      return changed ? next : prev;
    });
    initialLatestReleaseAppliedRef.current = true;
  }, [releaseVm.latestReleasedGroupPage]);

  useEffect(() => {
    function stopDrag() {
      dragRef.current.active = false;
      setIsTimelineDragging(false);
      cardsDragRef.current.active = false;
      setIsCardsRailDragging(false);
    }
    window.addEventListener("mouseup", stopDrag);
    return () => window.removeEventListener("mouseup", stopDrag);
  }, []);

  function groupBlockStyle(isExpanded) {
    return { width: isExpanded ? "min(90vw, 380px)" : "min(90vw, 344px)" };
  }

  async function copyShareLink() {
    try {
      await navigator.clipboard.writeText(window.location.href);
      setShareCopied(true);
      window.setTimeout(() => setShareCopied(false), 1200);
    } catch {
      // ignore clipboard errors
    }
  }

  return (
    <main className="app-shell">
      <header className="top">
        <div className="top-row">
          <h1>{roadmapData.title}</h1>
          <button type="button" className={shareCopied ? "share-btn copied" : "share-btn"} onClick={copyShareLink}>
            {shareCopied ? "Link Copied!" : "Copy Share Link"}
          </button>
        </div>
        <p>{roadmapData.subtitle}</p>
        <p className="muted">Last updated: {roadmapData.lastUpdated}</p>
        <div className="tabs">
          <button type="button" className={view === "cards" ? "active" : ""} onClick={() => setView("cards")}>
            RELEASE VIEW
          </button>
          <button type="button" className={view === "tracker" ? "active" : ""} onClick={() => setView("tracker")}>
            PROGRESS TRACKER
          </button>
        </div>
      </header>

      {view === "cards" ? (
        <ReleaseView
          collapseAllHierarchy={collapseAllHierarchy}
          setDefaultHierarchy={setDefaultHierarchy}
          expandAllHierarchy={expandAllHierarchy}
          isAllCollapsed={releaseVm.isAllCollapsed}
          isDefaultState={releaseVm.isDefaultState}
          isAllExpanded={releaseVm.isAllExpanded}
          targetGroupKeys={releaseVm.targetGroupKeys}
          targetCardIds={releaseVm.targetCardIds}
          latestReleaseLabel={releaseVm.latestReleaseLabel}
          isCardsRailDragging={isCardsRailDragging}
          cardsRailShellRef={cardsRailShellRef}
          handleCardsRailShellScroll={handleCardsRailShellScroll}
          cardsRailRef={cardsRailRef}
          showPastGroups={showPastGroups}
          pastGroups={releaseVm.pastGroups}
          groupCardPage={groupCardPage}
          expandedGroups={expandedGroups}
          groupBlockStyle={groupBlockStyle}
          toggleGroupExpanded={toggleGroupExpanded}
          scrollGroupToPage={scrollGroupToPage}
          setGroupRowRef={setGroupRowRef}
          handleGroupRowWheel={handleGroupRowWheel}
          handleGroupRowScroll={handleGroupRowScroll}
          groupRowHeights={groupRowHeights}
          expandedCards={expandedCards}
          toggleCardExpanded={toggleCardExpanded}
          openFeatureModal={openFeatureModal}
          historyDividerRef={historyDividerRef}
          togglePastReleases={togglePastReleases}
          activeGroups={releaseVm.activeGroups}
          hasCardsOverflow={hasCardsOverflow}
          cardsScrollProgress={cardsScrollProgress}
          setCardsScrollProgress={setCardsScrollProgress}
        />
      ) : (
        <section>
          {trackerVm.trackerPinMask && (
            <div
              className="tracker-pin-mask"
              aria-hidden="true"
              style={{
                left: `${trackerVm.trackerPinMask.left}px`,
                width: `${trackerVm.trackerPinMask.width}px`,
                height: `${trackerVm.trackerPinMask.height}px`,
              }}
            />
          )}
          <div className="tracker-pin-anchor" ref={trackerControlsPinAnchorRef} aria-hidden="true" />
          <TrackerControlRow
            trackerControlsFixed={trackerControlsFixed}
            trackerControlsTop={trackerControlsTop}
            trackerControlsRef={trackerControlsRef}
            setAllTopTracks={setAllTopTracks}
            areAllTopTracksCollapsed={trackerVm.areAllTopTracksCollapsed}
            areAllTopTracksExpanded={trackerVm.areAllTopTracksExpanded}
            topTrackIdsLength={trackerVm.topTrackIds.length}
            hideReleasedFeatures={hideReleasedFeatures}
            setHideReleasedFeatures={setHideReleasedFeatures}
            timelineZoom={timelineZoom}
            zoomOptions={ZOOM_OPTIONS}
            setTimelineZoom={setTimelineZoom}
            nudgeTimeline={nudgeTimeline}
            scrollTimelineToCurrent={scrollTimelineToCurrent}
            todayLineLeft={trackerVm.todayLineLeft}
          />
          {trackerControlsFixed.active && <div className="tracker-controls-placeholder" style={{ height: `${trackerControlsFixed.height}px` }} />}
          <TrackerTimeline
            timelineShellRef={timelineShellRef}
            timelineHeaderPinAnchorRef={timelineHeaderPinAnchorRef}
            trackerHeaderFixed={trackerHeaderFixed}
            timelineStickyHeaderRef={timelineStickyHeaderRef}
            unitCount={trackerVm.unitCount}
            zoomCellWidth={trackerVm.zoomCellWidth}
            trackerHeaderTop={trackerVm.trackerHeaderTop}
            timelineHeaderLeftRef={timelineHeaderLeftRef}
            timelineScrollLeft={timelineScrollLeft}
            quarterGroups={trackerVm.quarterGroups}
            timelineUnits={trackerVm.timelineUnits}
            trackerPrelaunchRatio={trackerVm.trackerPrelaunchRatio}
            isTimelineDragging={isTimelineDragging}
            timelineRef={timelineRef}
            timelineScrollLeftRef={timelineScrollLeftRef}
            setTimelineScrollLeft={setTimelineScrollLeft}
            handleTimelineMouseDown={handleTimelineMouseDown}
            handleTimelineMouseMove={handleTimelineMouseMove}
            handleTimelineMouseUp={handleTimelineMouseUp}
            rows={trackerVm.rows}
            activeTrackDetailId={activeTrackDetailId}
            hoveredRowId={hoveredRowId}
            setHoveredRowId={setHoveredRowId}
            setExpandedTracks={setExpandedTracks}
            setActiveTrackDetailId={setActiveTrackDetailId}
            barStyle={trackerVm.barStyle}
            todayLineLeft={trackerVm.todayLineLeft}
            trackFillClass={trackerVm.trackFillClass}
            months={trackerVm.months}
          />
        </section>
      )}

      <FeatureModal
        selectedFeature={selectedFeature}
        closeFeatureModal={closeFeatureModal}
        modalImageLoaded={modalImageLoaded}
        modalImages={modalImages}
        modalWheelRef={modalWheelRef}
        modalImageIndex={modalImageIndex}
        changeModalImage={changeModalImage}
        safeModalImageIndex={safeModalImageIndex}
        imageTransitionDir={imageTransitionDir}
        setModalImageLoaded={setModalImageLoaded}
      />
    </main>
  );
}
