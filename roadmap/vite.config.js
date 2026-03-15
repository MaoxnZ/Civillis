import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const isCi = process.env.GITHUB_ACTIONS === "true";

export default defineConfig({
  plugins: [react()],
  base: isCi ? "/Civillis/roadmap/" : "/",
});
