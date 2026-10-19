import { useEffect, useLayoutEffect } from "react";

export function useCardsLayoutSync({
  view,
  showPastGroups,
  pastGroups,
  activeGroups,
  groupedCards,
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
}) {
  function syncCardsFloatingScrollbar() {
    const shell = cardsRailShellRef.current;
    if (!shell || view !== "cards") return;
    const max = Math.max(0, shell.scrollWidth - shell.clientWidth);
    setHasCardsOverflow(max > 0);
    setCardsScrollProgress(max <= 0 ? 0 : shell.scrollLeft / max);
  }

  function handleCardsRailShellScroll(event) {
    cardsRailScrollLeftRef.current = event.currentTarget.scrollLeft;
    syncCardsFloatingScrollbar();
  }

  useEffect(() => {
    return () => {
      Object.values(groupRowObserversRef.current).forEach((obs) => {
        if (obs) obs.disconnect();
      });
      groupRowObserversRef.current = {};
    };
  }, [groupRowObserversRef]);

  useLayoutEffect(() => {
    syncCardsFloatingScrollbar();
    const shell = cardsRailShellRef.current;
    const onResize = () => syncCardsFloatingScrollbar();
    window.addEventListener("resize", onResize);
    let observer = null;
    if (shell && typeof ResizeObserver !== "undefined") {
      observer = new ResizeObserver(() => syncCardsFloatingScrollbar());
      observer.observe(shell);
    }
    return () => {
      window.removeEventListener("resize", onResize);
      if (observer) observer.disconnect();
    };
  }, [view, showPastGroups, expandedGroups, expandedCards, groupedCards]);

  useLayoutEffect(() => {
    if (view !== "cards") return;
    window.requestAnimationFrame(() => {
      Object.keys(groupRowRefs.current).forEach((key) => {
        syncGroupRowHeight(key);
        bindGroupRowObserver(key);
      });
    });
  }, [view, showPastGroups, expandedGroups, expandedCards, groupCardPage, groupedCards, groupRowRefs, syncGroupRowHeight, bindGroupRowObserver]);

  useEffect(() => {
    if (view === "cards") return;
    setHasCardsOverflow(false);
    setCardsScrollProgress(0);
  }, [view, setHasCardsOverflow, setCardsScrollProgress]);

  useEffect(() => {
    if (!showPastGroups) return;
    if (!pendingPastOpenAlignRef.current) return;
    let raf1 = 0;
    let raf2 = 0;
    const alignDividerToRight = () => {
      const shell = cardsRailShellRef.current;
      const divider = historyDividerRef.current;
      if (!shell || !divider) return;
      const target = Math.max(0, divider.offsetLeft + divider.offsetWidth - shell.clientWidth);
      const max = Math.max(0, shell.scrollWidth - shell.clientWidth);
      shell.scrollLeft = Math.max(0, Math.min(max, target));
      cardsRailScrollLeftRef.current = shell.scrollLeft;
      syncCardsFloatingScrollbar();
      pendingPastOpenAlignRef.current = false;
    };
    raf1 = window.requestAnimationFrame(() => {
      raf2 = window.requestAnimationFrame(alignDividerToRight);
    });
    return () => {
      if (raf1) window.cancelAnimationFrame(raf1);
      if (raf2) window.cancelAnimationFrame(raf2);
    };
  }, [showPastGroups, pastGroups.length, activeGroups.length]);

  useEffect(() => {
    if (view !== "cards") return;
    let raf = 0;
    let attempts = 0;
    const maxAttempts = 40;

    const restoreGroupRows = () => {
      let settled = true;
      Object.entries(groupRowRefs.current).forEach(([groupKey, row]) => {
        if (!(row instanceof HTMLElement)) return;
        if (row.clientWidth <= 0) return;
        const pageRaw = groupCardPage[groupKey] ?? 0;
        const page = Math.max(0, Math.min(row.children.length - 1, pageRaw));
        const target = page * row.clientWidth;
        const rowMax = Math.max(0, row.scrollWidth - row.clientWidth);
        const clampedTarget = Math.max(0, Math.min(rowMax, target));
        if (rowMax + 1 < target) settled = false;
        if (Math.abs(row.scrollLeft - clampedTarget) > 1) {
          row.scrollLeft = clampedTarget;
          settled = false;
        }
      });
      return settled;
    };

    const restoreWhenReady = () => {
      const shell = cardsRailShellRef.current;
      if (!shell) return;
      const max = Math.max(0, shell.scrollWidth - shell.clientWidth);
      const target = Math.max(0, Math.min(max, cardsRailScrollLeftRef.current));
      isRestoringGroupRowsRef.current = true;
      ignoreGroupScrollUntilRef.current = Date.now() + 600;
      if (max > 0 || target <= 0 || attempts >= maxAttempts) {
        if (Math.abs(shell.scrollLeft - target) > 1) {
          shell.scrollLeft = target;
        }
        const rowsSettled = restoreGroupRows();
        syncCardsFloatingScrollbar();
        if (rowsSettled || attempts >= maxAttempts) {
          isRestoringGroupRowsRef.current = false;
          ignoreGroupScrollUntilRef.current = Date.now() + 300;
          return;
        }
      }
      attempts += 1;
      raf = window.requestAnimationFrame(restoreWhenReady);
    };
    restoreWhenReady();

    return () => {
      if (raf) window.cancelAnimationFrame(raf);
      isRestoringGroupRowsRef.current = false;
      ignoreGroupScrollUntilRef.current = Date.now() + 200;
    };
  }, [view, showPastGroups, groupedCards.length, expandedGroups, expandedCards, groupCardPage, isRestoringGroupRowsRef, ignoreGroupScrollUntilRef]);

  useEffect(() => {
    if (view !== "cards") return;
    Object.entries(groupRowRefs.current).forEach(([groupKey, row]) => {
      if (!(row instanceof HTMLElement)) return;
      if (row.clientWidth <= 0) return;
      const pageRaw = groupCardPage[groupKey] ?? 0;
      const page = Math.max(0, Math.min(row.children.length - 1, pageRaw));
      const target = page * row.clientWidth;
      if (Math.abs(row.scrollLeft - target) > 1) {
        row.scrollLeft = target;
      }
      syncGroupRowHeight(groupKey, page);
      bindGroupRowObserver(groupKey, page);
    });
  }, [view, groupCardPage, showPastGroups, groupedCards.length, syncGroupRowHeight, bindGroupRowObserver, groupRowRefs]);

  return {
    syncCardsFloatingScrollbar,
    handleCardsRailShellScroll,
  };
}
