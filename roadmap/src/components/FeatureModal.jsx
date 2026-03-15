import { StatusPill } from "./roadmap-ui";
import { CIVILLIS_WATERMARK_PLACEHOLDER } from "../constants/image-placeholders";

const FALLBACK_IMAGE = CIVILLIS_WATERMARK_PLACEHOLDER;

export function FeatureModal({
  selectedFeature,
  closeFeatureModal,
  modalImageLoaded,
  modalImages,
  modalWheelRef,
  modalImageIndex,
  changeModalImage,
  safeModalImageIndex,
  imageTransitionDir,
  setModalImageLoaded,
}) {
  if (!selectedFeature) return null;
  return (
    <div
      className="modal-mask"
      onMouseDown={(e) => {
        if (e.target !== e.currentTarget) return;
        e.preventDefault();
        closeFeatureModal(true);
      }}
      onClick={(e) => {
        if (e.target === e.currentTarget) {
          e.preventDefault();
        }
      }}
    >
      <article className="modal-card" onClick={(e) => e.stopPropagation()}>
        <div className={`modal-image-wrap ${modalImageLoaded ? "loaded" : "loading"}`}>
          {modalImages.length > 1 && (
            <div
              className="modal-wheel-capture"
              onWheel={(e) => {
                e.preventDefault();
                const now = Date.now();
                const direction = e.deltaY === 0 ? 0 : e.deltaY > 0 ? 1 : -1;
                if (direction !== 0) {
                  if (modalWheelRef.current.lastDir !== 0 && direction !== modalWheelRef.current.lastDir) {
                    modalWheelRef.current.accum = 0;
                  }
                  modalWheelRef.current.lastDir = direction;
                  modalWheelRef.current.accum += e.deltaY;
                }
                if (now - modalWheelRef.current.lastTs < 140) return;
                if (Math.abs(modalWheelRef.current.accum) < 40) return;
                if (modalWheelRef.current.accum > 0) {
                  changeModalImage(modalImageIndex + 1, "next");
                } else {
                  changeModalImage(modalImageIndex - 1, "prev");
                }
                modalWheelRef.current.accum = 0;
                modalWheelRef.current.lastTs = now;
              }}
            />
          )}
          {!modalImageLoaded && <div className="modal-image-skeleton" aria-hidden="true" />}
          {modalImages.length > 0 && (
            <img
              className={imageTransitionDir === "prev" ? "slide-in-prev" : "slide-in-next"}
              key={`${safeModalImageIndex}-${modalImages[safeModalImageIndex] ?? "img"}`}
              src={modalImages[safeModalImageIndex] || FALLBACK_IMAGE}
              alt={selectedFeature.name}
              onLoad={() => setModalImageLoaded(true)}
              onError={(e) => {
                const img = e.currentTarget;
                if (img instanceof HTMLImageElement && img.dataset.fallbackApplied !== "1") {
                  img.dataset.fallbackApplied = "1";
                  img.src = FALLBACK_IMAGE;
                  return;
                }
                setModalImageLoaded(true);
              }}
            />
          )}
          {modalImages.length > 1 && (
            <>
              <button
                type="button"
                className="modal-nav modal-prev"
                onClick={() => changeModalImage(modalImageIndex - 1, "prev")}
              >
                ‹
              </button>
              <button
                type="button"
                className="modal-nav modal-next"
                onClick={() => changeModalImage(modalImageIndex + 1, "next")}
              >
                ›
              </button>
            </>
          )}
        </div>
        {modalImages.length > 0 && (
          <div className="modal-dots modal-progress">
            {modalImages.map((img, idx) => (
              <button
                type="button"
                key={`${img}-${idx}`}
                className={`${idx === safeModalImageIndex ? "dot active" : "dot"} ${modalImages.length === 1 ? "single" : ""}`}
                onClick={() => changeModalImage(idx, idx > safeModalImageIndex ? "next" : "prev")}
                aria-label={`image ${idx + 1}`}
                disabled={modalImages.length === 1}
              />
            ))}
          </div>
        )}
        <div className="modal-content">
          <div className="card-top">
            <h3>{selectedFeature.name}</h3>
            <StatusPill status={selectedFeature._status || selectedFeature.status} />
          </div>
          <p className="modal-details">{selectedFeature.details}</p>
          <ul>
            {(selectedFeature.items ?? []).map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
          {Array.isArray(selectedFeature.related) && selectedFeature.related.length > 0 && (
            <p className="feature-related">Related: {selectedFeature.related.join(", ")}</p>
          )}
        </div>
      </article>
    </div>
  );
}
