import { useEffect, useMemo } from "react";
import { compareVersionsAsc, pickLatestReleasedCard, topVersionKey } from "../utils/roadmap-utils";

export function useReleaseViewModel({ roadmapData, showPastGroups, expandedGroups, expandedCards, setExpandedGroups }) {
  const cards = useMemo(() => roadmapData.releaseCards ?? [], [roadmapData]);

  const latestReleasedCard = useMemo(() => pickLatestReleasedCard(cards), [cards]);
  const latestReleaseLabel = useMemo(() => {
    if (!latestReleasedCard) return "N/A";
    return `v${latestReleasedCard.version}-${String(latestReleasedCard.phase || "release").toLowerCase()}`;
  }, [latestReleasedCard]);

  const groupedCards = useMemo(() => {
    const phaseOrder = ["alpha", "beta", "release"];
    const map = new Map();
    cards.forEach((card) => {
      const phaseKey = String(card.phase || "release").toLowerCase();
      const streamKey = topVersionKey(card.version || card.window);
      const groupKey = `${phaseKey}::${streamKey}`;
      if (!map.has(groupKey)) {
        map.set(groupKey, { phase: phaseKey, stream: streamKey, items: [] });
      }
      map.get(groupKey).items.push(card);
    });
    return Array.from(map.entries())
      .map(([key, group]) => ({
        key,
        phase: group.phase,
        stream: group.stream,
        label: `${group.phase.toUpperCase()} ${group.stream}`,
        summaryTitle: group.items.find((item) => item.groupSummaryTitle)?.groupSummaryTitle || "",
        items: [...group.items].sort((a, b) => compareVersionsAsc(a.version, b.version)),
      }))
      .sort((a, b) => {
        const ia = phaseOrder.indexOf(a.phase);
        const ib = phaseOrder.indexOf(b.phase);
        if (ia === -1 && ib === -1) return a.key.localeCompare(b.key);
        if (ia === -1) return 1;
        if (ib === -1) return -1;
        if (ia !== ib) return ia - ib;
        return compareVersionsAsc(a.stream, b.stream);
      });
  }, [cards]);

  const latestReleasedGroupPage = useMemo(() => {
    if (!latestReleasedCard) return null;
    const phaseKey = String(latestReleasedCard.phase || "release").toLowerCase();
    const streamKey = topVersionKey(latestReleasedCard.version || latestReleasedCard.window);
    const groupKey = `${phaseKey}::${streamKey}`;
    const group = groupedCards.find((g) => g.key === groupKey);
    if (!group) return null;
    const page = group.items.findIndex((item) => item.id === latestReleasedCard.id);
    if (page < 0) return null;
    return { groupKey, page };
  }, [latestReleasedCard, groupedCards]);

  useEffect(() => {
    const next = {};
    groupedCards.forEach((g) => {
      if (expandedGroups[g.key] === undefined) next[g.key] = true;
    });
    if (Object.keys(next).length > 0) {
      setExpandedGroups((prev) => ({ ...next, ...prev }));
    }
  }, [groupedCards, expandedGroups, setExpandedGroups]);

  const releasedGroups = useMemo(
    () => groupedCards.filter((group) => group.items.every((c) => c.status.toLowerCase() === "released")),
    [groupedCards]
  );

  const reservedReleasedGroupKeys = useMemo(() => {
    const keep = releasedGroups.slice(-2);
    return new Set(keep.map((g) => g.key));
  }, [releasedGroups]);

  const pastGroups = useMemo(
    () => releasedGroups.filter((group) => !reservedReleasedGroupKeys.has(group.key)),
    [releasedGroups, reservedReleasedGroupKeys]
  );

  const activeGroups = useMemo(
    () =>
      groupedCards.filter(
        (group) => !group.items.every((c) => c.status.toLowerCase() === "released") || reservedReleasedGroupKeys.has(group.key)
      ),
    [groupedCards, reservedReleasedGroupKeys]
  );

  const targetGroups = useMemo(() => (showPastGroups ? groupedCards : activeGroups), [showPastGroups, groupedCards, activeGroups]);
  const targetGroupKeys = useMemo(() => targetGroups.map((group) => group.key), [targetGroups]);
  const targetCardIds = useMemo(() => targetGroups.flatMap((group) => group.items.map((card) => card.id)), [targetGroups]);

  const isAllExpanded = useMemo(() => {
    if (targetGroupKeys.length === 0 && targetCardIds.length === 0) return false;
    const groupsOpen = targetGroupKeys.every((key) => !!expandedGroups[key]);
    const cardsOpen = targetCardIds.every((id) => !!expandedCards[id]);
    return groupsOpen && cardsOpen;
  }, [targetGroupKeys, targetCardIds, expandedGroups, expandedCards]);

  const isAllCollapsed = useMemo(() => {
    if (targetGroupKeys.length === 0 && targetCardIds.length === 0) return false;
    const groupsClosed = targetGroupKeys.every((key) => !expandedGroups[key]);
    const cardsClosed = targetCardIds.every((id) => !expandedCards[id]);
    return groupsClosed && cardsClosed;
  }, [targetGroupKeys, targetCardIds, expandedGroups, expandedCards]);

  const isDefaultState = useMemo(() => {
    if (targetGroupKeys.length === 0 && targetCardIds.length === 0) return false;
    const groupsOpen = targetGroupKeys.every((key) => !!expandedGroups[key]);
    const cardsClosed = targetCardIds.every((id) => !expandedCards[id]);
    return groupsOpen && cardsClosed;
  }, [targetGroupKeys, targetCardIds, expandedGroups, expandedCards]);

  return {
    cards,
    latestReleaseLabel,
    latestReleasedGroupPage,
    groupedCards,
    pastGroups,
    activeGroups,
    targetGroups,
    targetGroupKeys,
    targetCardIds,
    isAllExpanded,
    isAllCollapsed,
    isDefaultState,
  };
}
