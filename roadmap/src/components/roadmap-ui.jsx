import { groupSummaryTitle } from "../utils/roadmap-utils";
import { CIVILLIS_WATERMARK_PLACEHOLDER } from "../constants/image-placeholders";

const FALLBACK_IMAGE = CIVILLIS_WATERMARK_PLACEHOLDER;

function featureStatus(cardStatus, featureStatusRaw) {
  return featureStatusRaw;
}

function withFallbackImage(event) {
  const img = event.currentTarget;
  if (!(img instanceof HTMLImageElement)) return;
  if (img.dataset.fallbackApplied === "1") return;
  img.dataset.fallbackApplied = "1";
  img.src = FALLBACK_IMAGE;
}

export function FilterChips({ value, options, onChange, className = "" }) {
  return (
    <div className={`chip-group ${className}`.trim()}>
      {options.map((opt) => (
        <button key={opt} type="button" className={value === opt ? "chip active" : "chip"} onClick={() => onChange(opt)}>
          {opt.replace(/\b\w/g, (m) => m.toUpperCase())}
        </button>
      ))}
    </div>
  );
}

export function StatusPill({ status }) {
  return <span className={`status-pill status-${status.toLowerCase().replace(/\s+/g, "-")}`}>{status}</span>;
}

export function CardCard({ card, expandedCards, onToggleCard, onFeatureOpen }) {
  return (
    <article className="card" data-card-id={card.id}>
      <img className="card-image" src={card.image || FALLBACK_IMAGE} alt={card.window} onError={withFallbackImage} />
      <div className="card-main">
        <div className="card-top">
          <h3>{card.window}</h3>
          <StatusPill status={card.status} />
        </div>
        <div className="card-body-scroll">
          <p className="theme">{card.theme}</p>
          <p className="card-summary">{card.summary}</p>
        </div>
        <button
          type="button"
          className={`card-expand-bar ${expandedCards[card.id] ? "open" : ""}`}
          onClick={() => onToggleCard(card.id)}
        >
          {expandedCards[card.id] ? "Hide features" : "Expand features"}
        </button>
      </div>
      <div className={`feature-list-wrap ${expandedCards[card.id] ? "open" : ""}`}>
        <div className="feature-list">
          {card.features.map((feature) => {
            const fStatus = featureStatus(card.status, feature.status);
            return (
              <button
                key={feature.id}
                type="button"
                className="feature-row"
                onClick={(e) => onFeatureOpen({ ...feature, _status: fStatus }, e.currentTarget)}
              >
                <div className={`feature-status-strip status-${fStatus.toLowerCase().replace(/\s+/g, "-")}`}>
                  <span>{fStatus}</span>
                </div>
                <div className="feature-main">
                  <strong>{feature.name}</strong>
                  {feature.image && <img className="feature-thumb" src={feature.image} alt={feature.name} onError={withFallbackImage} />}
                </div>
              </button>
            );
          })}
        </div>
      </div>
    </article>
  );
}

export function GroupSummaryCard({ group }) {
  const lead = group.items[0];
  const released = group.items.filter((item) => item.status.toLowerCase() === "released").length;
  const active = group.items.filter((item) => ["in progress", "under review"].includes(item.status.toLowerCase())).length;
  const tentative = group.items.filter((item) => item.status.toLowerCase() === "tentative").length;
  return (
    <article className="group-summary-card">
      {lead?.image && <img className="group-summary-image" src={lead.image} alt={`${group.label || group.key} overview`} onError={withFallbackImage} />}
      <div className="group-summary-main">
        <h4>{groupSummaryTitle(group)}</h4>
        <p className="group-summary-sub">{group.items.length} release cards in this stream.</p>
      </div>
      <div className="summary-segments" aria-label="summary status segments">
        <div className="seg seg-released">
          <span>{released}</span>
          <small>Released</small>
        </div>
        <div className="seg seg-progress">
          <span>{active}</span>
          <small>In Progress</small>
        </div>
        <div className="seg seg-planned">
          <span>{tentative}</span>
          <small>Tentative</small>
        </div>
      </div>
    </article>
  );
}
