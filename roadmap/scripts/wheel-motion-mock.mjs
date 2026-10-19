const MIN_GAP = 20;
const TARGET_Y = 800;
const EPS = 1e-6;

function easeInOut(t) {
  return t < 0.5 ? 2 * t * t : 1 - ((-2 * t + 2) ** 2) / 2;
}

function simulate({ mode, startBottom, deltaBottom, frames = 60 }) {
  const startY = Math.max(TARGET_Y, startBottom + MIN_GAP);
  let prevY = startY;
  const ys = [];
  for (let i = 0; i <= frames; i += 1) {
    const t = i / frames;
    const bottom = startBottom + deltaBottom * easeInOut(t);
    let y = Math.max(startY, bottom + MIN_GAP);
    if (mode === "expand") y = Math.max(prevY, y);
    else y = Math.min(prevY, y);
    prevY = y;
    ys.push(y);
  }
  return ys;
}

function assertNoBounce({ name, ys, mode }) {
  for (let i = 1; i < ys.length; i += 1) {
    const d = ys[i] - ys[i - 1];
    if (mode === "expand" && d < -EPS) {
      throw new Error(`${name}: bounce detected (expand has negative step at ${i})`);
    }
    if (mode === "collapse" && d > EPS) {
      throw new Error(`${name}: bounce detected (collapse has positive step at ${i})`);
    }
  }
}

function assertNoMoveBeforeThreshold({ name, ys }) {
  for (let i = 1; i < ys.length; i += 1) {
    if (Math.abs(ys[i] - ys[0]) > EPS) {
      throw new Error(`${name}: scrollbar moved before threshold touch`);
    }
  }
}

function run() {
  const cases = [
    {
      name: "expand-small-no-threshold",
      mode: "expand",
      startBottom: 640,
      deltaBottom: 60, // never reaches TARGET_Y - MIN_GAP
      expectNoMove: true,
    },
    {
      name: "expand-large-threshold-hit",
      mode: "expand",
      startBottom: 640,
      deltaBottom: 260,
      expectNoMove: false,
    },
    {
      name: "collapse-from-pushed-state",
      mode: "collapse",
      startBottom: 900,
      deltaBottom: -220,
      expectNoMove: false,
    },
  ];

  for (const c of cases) {
    const ys = simulate(c);
    assertNoBounce({ name: c.name, ys, mode: c.mode });
    if (c.expectNoMove) assertNoMoveBeforeThreshold({ name: c.name, ys });
  }
  console.log("wheel-motion-mock: PASS (no bounce, threshold behavior respected)");
}

try {
  run();
} catch (err) {
  console.error("wheel-motion-mock: FAIL");
  console.error(err instanceof Error ? err.message : String(err));
  process.exit(1);
}
