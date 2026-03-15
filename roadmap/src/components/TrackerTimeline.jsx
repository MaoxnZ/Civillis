import { durationDaysLabel, releasedInLabel } from "../utils/roadmap-utils";

export function TrackerTimeline({
  timelineShellRef,
  timelineHeaderPinAnchorRef,
  trackerHeaderFixed,
  timelineStickyHeaderRef,
  unitCount,
  zoomCellWidth,
  trackerHeaderTop,
  timelineHeaderLeftRef,
  timelineScrollLeft,
  quarterGroups,
  timelineUnits,
  trackerPrelaunchRatio,
  isTimelineDragging,
  timelineRef,
  timelineScrollLeftRef,
  setTimelineScrollLeft,
  handleTimelineMouseDown,
  handleTimelineMouseMove,
  handleTimelineMouseUp,
  rows,
  activeTrackDetailId,
  hoveredRowId,
  setHoveredRowId,
  setExpandedTracks,
  setActiveTrackDetailId,
  barStyle,
  todayLineLeft,
  trackFillClass,
  months,
}) {
  return (
    <section className="timeline-shell" ref={timelineShellRef}>
      <div className="timeline-pin-anchor" ref={timelineHeaderPinAnchorRef} aria-hidden="true" />
      <div
        className={`timeline-sticky-header ${trackerHeaderFixed.active ? "is-fixed" : ""}`}
        ref={timelineStickyHeaderRef}
        style={{
          "--months": unitCount,
          "--time-cell-w": `${zoomCellWidth}px`,
          ...(trackerHeaderFixed.active
            ? { top: `${trackerHeaderTop}px`, left: `${trackerHeaderFixed.left}px`, width: `${trackerHeaderFixed.width}px` }
            : {}),
        }}
      >
        <div className="timeline-header-left" ref={timelineHeaderLeftRef}>
          <span className="timeline-header-pipeline">PIPELINE</span>
          <span className="timeline-header-divider" aria-hidden="true">
            |
          </span>
          <span className="timeline-header-feature">feature</span>
        </div>
        <div className="timeline-header-right-viewport">
          <div className="timeline-header-right" style={{ transform: `translateX(${-timelineScrollLeft}px)` }}>
            <div className="quarter-row">
              {quarterGroups.map((group) => (
                <div key={`sticky-${group.label}-${group.count}`} className="quarter-cell" style={{ gridColumn: `span ${group.count}` }}>
                  {group.label}
                </div>
              ))}
            </div>
            {timelineUnits.map((unit) => (
              <div key={`sticky-${unit.key}`} className={unit.quarterBoundary ? "month-cell quarter-break" : "month-cell"}>
                {unit.label}
              </div>
            ))}
          </div>
        </div>
      </div>
      {trackerHeaderFixed.active && <div className="timeline-sticky-placeholder" style={{ height: `${trackerHeaderFixed.height}px` }} />}
      <div
        className={`timeline-scroll ${isTimelineDragging ? "dragging" : ""}`}
        ref={timelineRef}
        onScroll={(e) => {
          const next = e.currentTarget.scrollLeft;
          timelineScrollLeftRef.current = next;
          setTimelineScrollLeft(next);
        }}
        onMouseDown={handleTimelineMouseDown}
        onMouseMove={handleTimelineMouseMove}
        onMouseUp={handleTimelineMouseUp}
        onMouseLeave={handleTimelineMouseUp}
      >
        <div
          className="timeline-grid"
          style={{
            "--months": unitCount,
            "--time-cell-w": `${zoomCellWidth}px`,
            "--prelaunch-pct": `${(trackerPrelaunchRatio * 100).toFixed(4)}%`,
          }}
        >
          {trackerPrelaunchRatio > 0 && (
            <div className="timeline-prelaunch-watermark" aria-hidden="true">
              <span>CIVILLIS</span>
            </div>
          )}

          {rows.map((row) => {
            const isDetailOpen = row.depth === 1 && activeTrackDetailId === row.id;
            return (
              <div
                className={`timeline-row-wrap depth-${row.depth} ${hoveredRowId === row.id ? "is-hovered" : ""}`}
                key={row.id}
                onMouseEnter={() => setHoveredRowId(row.id)}
                onMouseLeave={() => setHoveredRowId(null)}
              >
                <div className="timeline-row">
                  <div
                    className={`timeline-left depth-${row.depth} ${row.depth === 0 && row.hasChildren ? "expandable" : ""} ${
                      row.depth === 1 ? "detail-toggle" : ""
                    } ${isDetailOpen ? "detail-open" : ""}`}
                    style={{ paddingLeft: "12px" }}
                    onClick={() => {
                      if (row.depth === 0 && row.hasChildren) {
                        setExpandedTracks((prev) => ({ ...prev, [row.id]: !prev[row.id] }));
                      } else if (row.depth === 1) {
                        setActiveTrackDetailId((prev) => (prev === row.id ? null : row.id));
                      }
                    }}
                  >
                    <span className="toggle-slot" aria-hidden={!(row.depth === 0 && row.hasChildren) && row.depth !== 1}>
                      {(row.depth === 0 && row.hasChildren) || row.depth === 1 ? (
                        <button
                          type="button"
                          className={`expand-btn ${row.depth === 0 ? "parent-toggle" : "child-toggle"}`}
                          onClick={(e) => {
                            e.stopPropagation();
                            if (row.depth === 0) {
                              setExpandedTracks((prev) => ({ ...prev, [row.id]: !prev[row.id] }));
                            } else {
                              setActiveTrackDetailId((prev) => (prev === row.id ? null : row.id));
                            }
                          }}
                          aria-label={row.depth === 0 ? (row.isOpen ? "collapse" : "expand") : isDetailOpen ? "collapse details" : "expand details"}
                        >
                          <span className={row.depth === 0 ? (row.isOpen ? "expand-icon open" : "expand-icon") : isDetailOpen ? "expand-icon open" : "expand-icon"}>
                            ▸
                          </span>
                        </button>
                      ) : null}
                    </span>
                    <div className="timeline-left-main">
                      {row.depth === 1 ? (
                        <div className="timeline-title-row">
                          <strong>{row.name}</strong>
                          {!isDetailOpen && (
                            <span className={`child-status-inline status-${row.status.toLowerCase().replace(/\s+/g, "-")}`}>{row.status}</span>
                          )}
                        </div>
                      ) : (
                        <strong>{row.name}</strong>
                      )}
                      {row.depth === 1 && (
                        <div
                          className={`track-detail-inline ${isDetailOpen ? "open" : ""}`}
                          aria-hidden={!isDetailOpen}
                          onClick={(e) => e.stopPropagation()}
                        >
                          <article className="track-inline-detail">
                            <div className="track-detail-meta">
                              <span>Duration: {durationDaysLabel(row.startDate, row.endDate, row.schedule, months)}</span>
                              {row.status === "Released" && row.releasedIn?.window && <span>{releasedInLabel(row.releasedIn)}</span>}
                            </div>
                            <p>{row.description || "Detailed delivery notes and sequencing live here for this workstream."}</p>
                            {Array.isArray(row.subItems) && row.subItems.length > 0 && (
                              <ul className="track-detail-list">
                                {row.subItems.map((item) => (
                                  <li key={item.id}>
                                    <strong>{item.name}</strong> - {item.status}
                                  </li>
                                ))}
                              </ul>
                            )}
                            <div className="track-detail-progress" aria-label="progress meter">
                              <div className="track-meter thin detail-meter">
                                <div className={`track-meter-fill ${trackFillClass(row.progress)}`} style={{ width: `${Math.max(0, row.progress)}%` }} />
                                <span className="track-meter-label">{row.progress <= 0 ? "Tentative" : `${row.progress}%`}</span>
                              </div>
                              <span className={`child-status-inline detail-status-inline status-${row.status.toLowerCase().replace(/\s+/g, "-")}`}>
                                {row.status}
                              </span>
                            </div>
                          </article>
                        </div>
                      )}
                    </div>
                  </div>
                  <div className={`timeline-right depth-${row.depth}`}>
                    {timelineUnits.map((unit) => (
                      <div key={`${row.id}-${unit.key}`} className="time-cell" />
                    ))}
                    <div className={`time-bar depth-${row.depth}`} style={barStyle(row, row.depth)} />
                    {todayLineLeft && <div className="today-line" style={{ left: todayLineLeft }} />}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
