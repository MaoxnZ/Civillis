import { CardCard, GroupSummaryCard } from "./roadmap-ui";
import { entryCountLabel, groupSummaryTitle } from "../utils/roadmap-utils";

export function ReleaseView({
  collapseAllHierarchy,
  setDefaultHierarchy,
  expandAllHierarchy,
  isAllCollapsed,
  isDefaultState,
  isAllExpanded,
  targetGroupKeys,
  targetCardIds,
  latestReleaseLabel,
  isCardsRailDragging,
  cardsRailShellRef,
  handleCardsRailShellScroll,
  cardsRailRef,
  showPastGroups,
  pastGroups,
  groupCardPage,
  expandedGroups,
  groupBlockStyle,
  toggleGroupExpanded,
  scrollGroupToPage,
  setGroupRowRef,
  handleGroupRowWheel,
  handleGroupRowScroll,
  groupRowHeights,
  expandedCards,
  toggleCardExpanded,
  openFeatureModal,
  historyDividerRef,
  togglePastReleases,
  activeGroups,
  hasCardsOverflow,
  cardsScrollProgress,
  setCardsScrollProgress,
}) {
  const allGroups = [...pastGroups, ...activeGroups];
  const latestReleasedGroupIndex = allGroups.reduce((acc, group, idx) => {
    const hasReleased = group.items.some((item) => String(item.status || "").toLowerCase() === "released");
    return hasReleased ? idx : acc;
  }, -1);

  function isPureTentativeGroup(group) {
    return group.items.length > 0 && group.items.every((item) => String(item.status || "").toLowerCase() === "tentative");
  }

  function earStateForGroup(group) {
    const idx = allGroups.findIndex((g) => g.key === group.key);
    const statuses = group.items.map((item) => String(item.status || "").toLowerCase());
    const hasReleased = statuses.includes("released");
    const hasInProgressLike = statuses.includes("in progress") || statuses.includes("under review");
    const hasNonReleased = statuses.some((status) => status !== "released");

    if (isPureTentativeGroup(group)) return "gray";
    if (hasInProgressLike) return "yellow";
    if (!hasReleased) return "gray";
    if (hasNonReleased) return "yellow";

    if (idx >= 0 && latestReleasedGroupIndex >= 0) {
      if (idx < latestReleasedGroupIndex) return "green";
      if (idx === latestReleasedGroupIndex) {
        const nextGroup = allGroups[idx + 1];
        if (nextGroup && isPureTentativeGroup(nextGroup)) return "yellow";
      }
    }
    return "green";
  }

  return (
    <section>
      <div className="control-row">
        <div className="dual-action" role="group" aria-label="cards expand actions">
          <button
            type="button"
            className="dual-btn"
            onClick={collapseAllHierarchy}
            disabled={isAllCollapsed || (targetGroupKeys.length === 0 && targetCardIds.length === 0)}
          >
            Collapse All
          </button>
          <button
            type="button"
            className="dual-btn"
            onClick={setDefaultHierarchy}
            disabled={isDefaultState || (targetGroupKeys.length === 0 && targetCardIds.length === 0)}
          >
            Default
          </button>
          <button
            type="button"
            className="dual-btn"
            onClick={expandAllHierarchy}
            disabled={isAllExpanded || (targetGroupKeys.length === 0 && targetCardIds.length === 0)}
          >
            Expand All
          </button>
        </div>
        <div className="cards-latest-release" aria-live="polite">
          Latest Release: <strong>{latestReleaseLabel}</strong>
        </div>
      </div>
      <section className={`cards-rail-shell ${isCardsRailDragging ? "dragging" : ""}`} ref={cardsRailShellRef} onScroll={handleCardsRailShellScroll}>
        <div className="cards-rail" ref={cardsRailRef}>
          {showPastGroups &&
            pastGroups.map((group) => {
              const railKey = `past-${group.key}`;
              const currentPage = groupCardPage[railKey] ?? 0;
              return (
                <section
                  className={`version-group rail-block ear-${earStateForGroup(group)} ${expandedGroups[group.key] ? "expanded" : "collapsed"}`}
                  key={`past-${group.key}`}
                  style={groupBlockStyle(!!expandedGroups[group.key])}
                >
                  <button type="button" className="version-cap mini" onClick={() => toggleGroupExpanded(group)}>
                    <span className="cap-arrow">{expandedGroups[group.key] ? "▷" : "◀"}</span>
                    <strong>{group.label || group.key}</strong>
                    <span className="cap-summary">{entryCountLabel(group.items.length)}</span>
                    <span className="cap-arrow">{expandedGroups[group.key] ? "◁" : "▶"}</span>
                  </button>
                  {expandedGroups[group.key] && (
                    <>
                      <div className="group-page-indicators" role="tablist" aria-label={`${group.label || group.key} pages`}>
                        {group.items.map((card, idx) => (
                          <button
                            type="button"
                            key={`${railKey}-page-${card.id}`}
                            className={`${idx === currentPage ? "page-seg active" : "page-seg"} ${group.items.length === 1 ? "single" : ""}`}
                            onClick={() => scrollGroupToPage(railKey, group.items.length, idx, true)}
                            aria-label={`go to card ${idx + 1}`}
                            disabled={group.items.length === 1}
                          />
                        ))}
                      </div>
                      <p className="group-summary-line">{groupSummaryTitle(group)}</p>
                      <div className="group-cards-row-shell">
                        <button
                          type="button"
                          className="group-nav-btn left"
                          onClick={() => scrollGroupToPage(railKey, group.items.length, currentPage - 1, true)}
                          disabled={group.items.length <= 1 || currentPage <= 0}
                          aria-label="previous version card"
                        >
                          ‹
                        </button>
                        <div
                          className="group-cards-row discrete"
                          ref={(node) => setGroupRowRef(railKey, node)}
                          onWheel={(e) => handleGroupRowWheel(railKey, group.items.length, e)}
                          onScroll={() => handleGroupRowScroll(railKey)}
                          style={groupRowHeights[railKey] ? { height: `${groupRowHeights[railKey]}px` } : undefined}
                        >
                          {group.items.map((card) => (
                            <CardCard
                              key={card.id}
                              card={card}
                              expandedCards={expandedCards}
                              onToggleCard={toggleCardExpanded}
                              onFeatureOpen={openFeatureModal}
                            />
                          ))}
                        </div>
                        <button
                          type="button"
                          className="group-nav-btn right"
                          onClick={() => scrollGroupToPage(railKey, group.items.length, currentPage + 1, true)}
                          disabled={group.items.length <= 1 || currentPage >= group.items.length - 1}
                          aria-label="next version card"
                        >
                          ›
                        </button>
                      </div>
                    </>
                  )}
                  {!expandedGroups[group.key] && (
                    <div className="summary-row">
                      <GroupSummaryCard group={group} />
                    </div>
                  )}
                </section>
              );
            })}

          {pastGroups.length > 0 && (
            <button type="button" ref={historyDividerRef} className={`history-divider ${showPastGroups ? "open" : ""}`} onClick={togglePastReleases}>
              <span>{showPastGroups ? "▶ HIDE PREV RELEASES" : "▶ SHOW PREV RELEASES"}</span>
            </button>
          )}

          {activeGroups.map((group) => {
            const railKey = `active-${group.key}`;
            const currentPage = groupCardPage[railKey] ?? 0;
            return (
              <section
                className={`version-group rail-block ear-${earStateForGroup(group)} ${expandedGroups[group.key] ? "expanded" : "collapsed"}`}
                key={group.key}
                style={groupBlockStyle(!!expandedGroups[group.key])}
              >
                <button type="button" className="version-cap" onClick={() => toggleGroupExpanded(group)}>
                  <span className="cap-arrow">{expandedGroups[group.key] ? "▷" : "◀"}</span>
                  <strong>{group.label || group.key}</strong>
                  <span className="cap-summary">{entryCountLabel(group.items.length)}</span>
                  <span className="cap-arrow">{expandedGroups[group.key] ? "◁" : "▶"}</span>
                </button>
                {expandedGroups[group.key] && (
                  <>
                    <div className="group-page-indicators" role="tablist" aria-label={`${group.label || group.key} pages`}>
                      {group.items.map((card, idx) => (
                        <button
                          type="button"
                          key={`${railKey}-page-${card.id}`}
                          className={`${idx === currentPage ? "page-seg active" : "page-seg"} ${group.items.length === 1 ? "single" : ""}`}
                          onClick={() => scrollGroupToPage(railKey, group.items.length, idx, true)}
                          aria-label={`go to card ${idx + 1}`}
                          disabled={group.items.length === 1}
                        />
                      ))}
                    </div>
                    <p className="group-summary-line">{groupSummaryTitle(group)}</p>
                    <div className="group-cards-row-shell">
                      <button
                        type="button"
                        className="group-nav-btn left"
                        onClick={() => scrollGroupToPage(railKey, group.items.length, currentPage - 1, true)}
                        disabled={group.items.length <= 1 || currentPage <= 0}
                        aria-label="previous version card"
                      >
                        ‹
                      </button>
                      <div
                        className="group-cards-row discrete"
                        ref={(node) => setGroupRowRef(railKey, node)}
                        onWheel={(e) => handleGroupRowWheel(railKey, group.items.length, e)}
                        onScroll={() => handleGroupRowScroll(railKey)}
                        style={groupRowHeights[railKey] ? { height: `${groupRowHeights[railKey]}px` } : undefined}
                      >
                        {group.items.map((card) => (
                          <CardCard
                            key={card.id}
                            card={card}
                            expandedCards={expandedCards}
                            onToggleCard={toggleCardExpanded}
                            onFeatureOpen={openFeatureModal}
                          />
                        ))}
                      </div>
                      <button
                        type="button"
                        className="group-nav-btn right"
                        onClick={() => scrollGroupToPage(railKey, group.items.length, currentPage + 1, true)}
                        disabled={group.items.length <= 1 || currentPage >= group.items.length - 1}
                        aria-label="next version card"
                      >
                        ›
                      </button>
                    </div>
                  </>
                )}
                {!expandedGroups[group.key] && (
                  <div className="summary-row">
                    <GroupSummaryCard group={group} />
                  </div>
                )}
              </section>
            );
          })}
        </div>
      </section>
      {hasCardsOverflow && (
        <div className="cards-floating-scrollbar" aria-hidden="true">
          <input
            type="range"
            min={0}
            max={1000}
            step={1}
            value={Math.round(cardsScrollProgress * 1000)}
            onChange={(event) => {
              const shell = cardsRailShellRef.current;
              if (!shell) return;
              const nextProgress = Number(event.target.value) / 1000;
              const max = Math.max(0, shell.scrollWidth - shell.clientWidth);
              shell.scrollLeft = max * nextProgress;
              setCardsScrollProgress(nextProgress);
            }}
          />
        </div>
      )}
    </section>
  );
}
