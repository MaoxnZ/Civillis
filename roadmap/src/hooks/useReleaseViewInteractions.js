export function useReleaseViewInteractions({
  targetGroups,
  showPastGroups,
  pastGroups,
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
  syncCardsFloatingScrollbar = null,
}) {
  function expandAllHierarchy() {
    const groupPatch = {};
    targetGroups.forEach((group) => {
      groupPatch[group.key] = true;
    });
    const cardPatch = {};
    targetGroups.forEach((group) => {
      group.items.forEach((card) => {
        cardPatch[card.id] = true;
      });
    });
    if (!showPastGroups) {
      pastGroups.forEach((group) => {
        groupPatch[group.key] = true;
        group.items.forEach((card) => {
          cardPatch[card.id] = false;
        });
      });
    }
    setExpandedGroups((prev) => ({ ...prev, ...groupPatch }));
    setExpandedCards((prev) => ({ ...prev, ...cardPatch }));
  }

  function collapseAllHierarchy() {
    const groupPatch = {};
    targetGroups.forEach((group) => {
      groupPatch[group.key] = false;
    });
    const cardPatch = {};
    targetGroups.forEach((group) => {
      group.items.forEach((card) => {
        cardPatch[card.id] = false;
      });
    });
    if (!showPastGroups) {
      pastGroups.forEach((group) => {
        groupPatch[group.key] = true;
        group.items.forEach((card) => {
          cardPatch[card.id] = false;
        });
      });
    }
    setExpandedGroups((prev) => ({ ...prev, ...groupPatch }));
    setExpandedCards((prev) => ({ ...prev, ...cardPatch }));
  }

  function setDefaultHierarchy() {
    const groupPatch = {};
    targetGroups.forEach((group) => {
      groupPatch[group.key] = true;
    });
    const cardPatch = {};
    targetGroups.forEach((group) => {
      group.items.forEach((card) => {
        cardPatch[card.id] = false;
      });
    });
    if (!showPastGroups) {
      pastGroups.forEach((group) => {
        groupPatch[group.key] = true;
        group.items.forEach((card) => {
          cardPatch[card.id] = false;
        });
      });
    }
    setExpandedGroups((prev) => ({ ...prev, ...groupPatch }));
    setExpandedCards((prev) => ({ ...prev, ...cardPatch }));
  }

  function togglePastReleases() {
    setShowPastGroups((prev) => {
      const next = !prev;
      if (next) {
        pendingPastOpenAlignRef.current = true;
        const patch = {};
        const cardsPatch = {};
        pastGroups.forEach((group) => {
          patch[group.key] = true;
          group.items.forEach((card) => {
            cardsPatch[card.id] = false;
          });
        });
        setExpandedGroups((prevExpanded) => ({ ...prevExpanded, ...patch }));
        setExpandedCards((prevCards) => ({ ...prevCards, ...cardsPatch }));
      }
      return next;
    });
  }

  function toggleGroupExpanded(group) {
    setExpandedGroups((prev) => {
      const currentlyOpen = !!prev[group.key];
      const next = { ...prev, [group.key]: !currentlyOpen };
      if (currentlyOpen) {
        const resetPatch = {};
        group.items.forEach((card) => {
          resetPatch[card.id] = false;
        });
        setExpandedCards((prevCards) => ({ ...prevCards, ...resetPatch }));
        setGroupCardPage((prevPage) => ({
          ...prevPage,
          [`active-${group.key}`]: 0,
          [`past-${group.key}`]: 0,
        }));
      }
      return next;
    });
  }

  function setGroupRowRef(groupKey, node) {
    if (!node) {
      const obs = groupRowObserversRef.current[groupKey];
      if (obs) {
        obs.disconnect();
        delete groupRowObserversRef.current[groupKey];
      }
      delete groupRowRefs.current[groupKey];
      return;
    }
    groupRowRefs.current[groupKey] = node;
    window.requestAnimationFrame(() => {
      syncGroupRowHeight(groupKey);
      bindGroupRowObserver(groupKey);
    });
  }

  function syncGroupRowHeight(groupKey, pageOverride = null) {
    const row = groupRowRefs.current[groupKey];
    if (!row) return;
    const cardsInRow = row.children;
    if (!cardsInRow || cardsInRow.length === 0) return;
    const pageRaw = pageOverride ?? groupCardPage[groupKey] ?? 0;
    const page = Math.max(0, Math.min(cardsInRow.length - 1, pageRaw));
    const cardEl = cardsInRow[page];
    if (!(cardEl instanceof HTMLElement)) return;
    const rectHeight = cardEl.getBoundingClientRect().height;
    const styles = window.getComputedStyle(cardEl);
    const marginTop = Number.parseFloat(styles.marginTop || "0") || 0;
    const marginBottom = Number.parseFloat(styles.marginBottom || "0") || 0;
    const nextHeight = Math.max(1, Math.ceil(rectHeight + marginTop + marginBottom + 2));
    setGroupRowHeights((prev) => (prev[groupKey] === nextHeight ? prev : { ...prev, [groupKey]: nextHeight }));
  }

  function bindGroupRowObserver(groupKey, pageOverride = null) {
    const row = groupRowRefs.current[groupKey];
    if (!row || typeof ResizeObserver === "undefined") return;
    const cardsInRow = row.children;
    if (!cardsInRow || cardsInRow.length === 0) return;
    const pageRaw = pageOverride ?? groupCardPage[groupKey] ?? 0;
    const page = Math.max(0, Math.min(cardsInRow.length - 1, pageRaw));
    const cardEl = cardsInRow[page];
    if (!(cardEl instanceof HTMLElement)) return;
    const prev = groupRowObserversRef.current[groupKey];
    if (prev) prev.disconnect();
    const obs = new ResizeObserver(() => {
      syncGroupRowHeight(groupKey, page);
    });
    obs.observe(cardEl);
    groupRowObserversRef.current[groupKey] = obs;
  }

  function scrollGroupToPage(groupKey, itemCount, requestedPage, smooth = true) {
    const row = groupRowRefs.current[groupKey];
    if (!row) return;
    const page = Math.max(0, Math.min(itemCount - 1, requestedPage));
    setGroupCardPage((prev) => ({ ...prev, [groupKey]: page }));
    row.scrollTo({ left: page * row.clientWidth, behavior: smooth ? "smooth" : "auto" });
    syncGroupRowHeight(groupKey, page);
    bindGroupRowObserver(groupKey, page);
  }

  function handleGroupRowScroll(groupKey) {
    if (isRestoringGroupRowsRef?.current) return;
    if ((ignoreGroupScrollUntilRef?.current ?? 0) > Date.now()) return;
    const row = groupRowRefs.current[groupKey];
    if (!row || row.clientWidth <= 0) return;
    const page = Math.round(row.scrollLeft / row.clientWidth);
    setGroupCardPage((prev) => (prev[groupKey] === page ? prev : { ...prev, [groupKey]: page }));
    syncGroupRowHeight(groupKey, page);
    bindGroupRowObserver(groupKey, page);
  }

  function handleGroupRowWheel(groupKey, itemCount, event) {
    const target = event.target;
    if (
      target instanceof HTMLElement &&
      target.closest(".card-body-scroll, .feature-list-wrap, .feature-list, .feature-row, .feature-main, .feature-status-strip")
    ) {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    if (itemCount <= 1) return;
    const delta = Math.abs(event.deltaY) >= Math.abs(event.deltaX) ? event.deltaY : event.deltaX;
    if (Math.abs(delta) < 10) return;
    const now = Date.now();
    const state = groupWheelRefs.current[groupKey] ?? { lastTs: 0 };
    if (now - state.lastTs < 120) return;
    const row = groupRowRefs.current[groupKey];
    const current =
      row instanceof HTMLElement && row.clientWidth > 0
        ? Math.round(row.scrollLeft / row.clientWidth)
        : groupCardPage[groupKey] ?? 0;
    const nextPage = delta > 0 ? current + 1 : current - 1;
    scrollGroupToPage(groupKey, itemCount, nextPage, true);
    groupWheelRefs.current[groupKey] = { lastTs: now };
  }

  function toggleCardExpanded(cardId) {
    setExpandedCards((prev) => ({ ...prev, [cardId]: !prev[cardId] }));
    if (typeof syncCardsFloatingScrollbar === "function") {
      window.setTimeout(syncCardsFloatingScrollbar, 240);
    }
    window.setTimeout(() => {
      Object.keys(groupRowRefs.current).forEach((key) => syncGroupRowHeight(key));
    }, 40);
    window.setTimeout(() => {
      Object.keys(groupRowRefs.current).forEach((key) => syncGroupRowHeight(key));
    }, 260);
  }

  return {
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
  };
}
