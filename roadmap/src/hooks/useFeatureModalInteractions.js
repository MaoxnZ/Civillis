import { useEffect, useMemo, useRef, useState } from "react";

export function useFeatureModalInteractions() {
  const [selectedFeature, setSelectedFeature] = useState(null);
  const [modalImageIndex, setModalImageIndex] = useState(0);
  const [modalImageLoaded, setModalImageLoaded] = useState(false);
  const [imageTransitionDir, setImageTransitionDir] = useState("next");
  const lastFeatureTriggerRef = useRef(null);
  const modalWheelRef = useRef({ accum: 0, lastTs: 0, lastDir: 0 });

  const modalImages = useMemo(() => {
    if (!selectedFeature) return [];
    return Array.isArray(selectedFeature.images) ? selectedFeature.images : [];
  }, [selectedFeature]);

  const safeModalImageIndex = modalImages.length > 0 ? Math.min(modalImageIndex, modalImages.length - 1) : 0;

  function openFeatureModal(feature, triggerElement) {
    lastFeatureTriggerRef.current = triggerElement ?? null;
    setImageTransitionDir("next");
    modalWheelRef.current = { accum: 0, lastTs: 0, lastDir: 0 };
    setSelectedFeature(feature);
  }

  function closeFeatureModal(restoreFocus = false) {
    const focusTarget = restoreFocus && lastFeatureTriggerRef.current instanceof HTMLElement ? lastFeatureTriggerRef.current : null;
    setSelectedFeature(null);
    if (focusTarget) {
      focusTarget.focus({ preventScroll: true });
    }
  }

  function changeModalImage(nextIndex, direction = "next") {
    if (modalImages.length === 0) return;
    const normalized = ((nextIndex % modalImages.length) + modalImages.length) % modalImages.length;
    if (normalized === modalImageIndex) return;
    setImageTransitionDir(direction);
    setModalImageLoaded(false);
    setModalImageIndex(normalized);
  }

  useEffect(() => {
    setModalImageIndex(0);
  }, [selectedFeature]);

  useEffect(() => {
    setModalImageLoaded(false);
  }, [selectedFeature, safeModalImageIndex]);

  useEffect(() => {
    function onKeyDown(event) {
      if (!selectedFeature) return;
      if (event.key === "Escape") {
        closeFeatureModal(true);
      } else if (event.key === "ArrowLeft" && modalImages.length > 1) {
        event.preventDefault();
        changeModalImage(modalImageIndex - 1, "prev");
      } else if (event.key === "ArrowRight" && modalImages.length > 1) {
        event.preventDefault();
        changeModalImage(modalImageIndex + 1, "next");
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [selectedFeature, modalImages, modalImageIndex]);

  return {
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
  };
}
