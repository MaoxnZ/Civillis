import { useEffect } from "react";

export function useCardsGlobalGestures({
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
}) {
  useEffect(() => {
    function shouldBlockCardsGlobalGesture(target) {
      if (!(target instanceof HTMLElement)) return true;
      return Boolean(
        target.closest(
          ".top, .control-row, .tabs, .card, .card-body-scroll, .feature-row, .cards-floating-scrollbar, .modal-mask, button, a, input, textarea, select, option, label"
        )
      );
    }

    function onWindowWheel(event) {
      if (view !== "cards") return;
      const target = event.target;
      if (!(target instanceof HTMLElement)) return;
      if (target.closest(".modal-mask")) return;
      if (target.closest(".card-body-scroll")) return;
      if (target.closest(".feature-list-wrap.open")) return;

      // Keep wheel-based discrete switching inside hat rows only.
      const row = target.closest(".group-cards-row.discrete");
      const inCard = target.closest(".card");
      if (!inCard || !(row instanceof HTMLElement)) return;
      let groupKey = null;
      for (const [key, node] of Object.entries(groupRowRefs.current)) {
        if (node === row) {
          groupKey = key;
          break;
        }
      }
      if (!groupKey) return;
      event.preventDefault();
      event.stopPropagation();
      handleGroupRowWheel(groupKey, row.children.length, event);
    }

    function onWindowMouseDown(event) {
      if (view !== "cards") return;
      const shell = cardsRailShellRef.current;
      if (!shell || !hasCardsOverflow) return;
      if (event.button !== 0) return;
      if (shouldBlockCardsGlobalGesture(event.target)) return;
      cardsDragRef.current.active = true;
      cardsDragRef.current.startX = event.clientX;
      cardsDragRef.current.startScrollLeft = shell.scrollLeft;
      setIsCardsRailDragging(true);
      event.preventDefault();
    }

    function onWindowMouseMove(event) {
      if (!cardsDragRef.current.active) return;
      const shell = cardsRailShellRef.current;
      if (!shell) return;
      const dx = event.clientX - cardsDragRef.current.startX;
      shell.scrollLeft = cardsDragRef.current.startScrollLeft - dx;
      cardsRailScrollLeftRef.current = shell.scrollLeft;
      syncCardsFloatingScrollbar();
    }

    function onWindowMouseUp() {
      if (!cardsDragRef.current.active) return;
      cardsDragRef.current.active = false;
      setIsCardsRailDragging(false);
    }

    window.addEventListener("wheel", onWindowWheel, { passive: false, capture: true });
    window.addEventListener("mousedown", onWindowMouseDown);
    window.addEventListener("mousemove", onWindowMouseMove);
    window.addEventListener("mouseup", onWindowMouseUp);
    return () => {
      window.removeEventListener("wheel", onWindowWheel, true);
      window.removeEventListener("mousedown", onWindowMouseDown);
      window.removeEventListener("mousemove", onWindowMouseMove);
      window.removeEventListener("mouseup", onWindowMouseUp);
    };
  }, [
    view,
    hasCardsOverflow,
    cardsRailShellRef,
    cardsDragRef,
    cardsRailScrollLeftRef,
    groupRowRefs,
    handleGroupRowWheel,
    setIsCardsRailDragging,
    syncCardsFloatingScrollbar,
  ]);

  useEffect(() => {
    function clearFeatureHighlightOnBlankClick(event) {
      if (selectedFeature) return;
      const target = event.target;
      if (!(target instanceof HTMLElement)) return;
      if (target.closest(".feature-row")) return;
      const active = document.activeElement;
      if (active instanceof HTMLElement && active.classList.contains("feature-row")) {
        active.blur();
      }
    }

    window.addEventListener("mousedown", clearFeatureHighlightOnBlankClick, true);
    return () => window.removeEventListener("mousedown", clearFeatureHighlightOnBlankClick, true);
  }, [selectedFeature]);
}
