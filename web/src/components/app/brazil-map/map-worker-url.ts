import workerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?url";

/** O sufixo muda a URL para ignorar a cópia em cache, que foi servida com um tipo que o navegador bloqueia. */
export const mapWorkerUrl = `${workerUrl}?v=2`;
