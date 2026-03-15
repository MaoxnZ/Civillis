import { FilterChips } from "./roadmap-ui";

export function TrackerControlRow({
  trackerControlsFixed,
  trackerControlsTop,
  trackerControlsRef,
  setAllTopTracks,
  areAllTopTracksCollapsed,
  areAllTopTracksExpanded,
  topTrackIdsLength,
  hideReleasedFeatures,
  setHideReleasedFeatures,
  timelineZoom,
  zoomOptions,
  setTimelineZoom,
  nudgeTimeline,
  scrollTimelineToCurrent,
  todayLineLeft,
}) {
  return (
    <div
      className={`control-row tracker-controls ${trackerControlsFixed.active ? "is-fixed" : ""}`}
      ref={trackerControlsRef}
      style={
        trackerControlsFixed.active
          ? { top: `${trackerControlsTop}px`, left: `${trackerControlsFixed.left}px`, width: `${trackerControlsFixed.width}px` }
          : undefined
      }
    >
      <div className="tracker-left-controls">
        <div className="dual-action tracker-expand-bar" role="group" aria-label="tracker expand actions">
          <button
            type="button"
            className="dual-btn"
            onClick={() => setAllTopTracks(false)}
            disabled={areAllTopTracksCollapsed || topTrackIdsLength === 0}
          >
            Collapse All
          </button>
          <button
            type="button"
            className="dual-btn"
            onClick={() => setAllTopTracks(true)}
            disabled={areAllTopTracksExpanded || topTrackIdsLength === 0}
          >
            Expand All
          </button>
        </div>
        <button
          type="button"
          className={`tracker-release-toggle ${hideReleasedFeatures ? "active" : ""}`}
          onClick={() => setHideReleasedFeatures((prev) => !prev)}
          aria-pressed={hideReleasedFeatures}
        >
          <span className="tracker-release-radio" aria-hidden="true" />
          <span>{hideReleasedFeatures ? "Show Released" : "Hide Released"}</span>
        </button>
      </div>
      <div className="tracker-time-controls">
        <FilterChips value={timelineZoom} options={zoomOptions} onChange={setTimelineZoom} className="tracker-zoom-chips" />
        <span className="tracker-time-connector" aria-hidden="true" />
        <div className="timeline-move-bar" role="group" aria-label="timeline quick navigation">
          <button type="button" className="move-seg" onClick={() => nudgeTimeline(-1)} aria-label="move timeline left">
            ◀
          </button>
          <button type="button" className="move-seg current" onClick={scrollTimelineToCurrent} disabled={!todayLineLeft}>
            Current
          </button>
          <button type="button" className="move-seg" onClick={() => nudgeTimeline(1)} aria-label="move timeline right">
            ▶
          </button>
        </div>
      </div>
    </div>
  );
}
