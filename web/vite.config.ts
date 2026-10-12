import { defineConfig } from 'vite';

// Dev: `npm run dev` on :5173 proxies the API and WebSocket to the Spring Boot server on :8080.
// Build: output goes straight into the jar's resources so Spring Boot serves it from /static.
export default defineConfig({
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
  build: {
    outDir: 'target/classes/static',
    emptyOutDir: true,
  },
});
