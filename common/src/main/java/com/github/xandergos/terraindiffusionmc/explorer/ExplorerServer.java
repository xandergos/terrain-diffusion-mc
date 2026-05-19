package com.github.xandergos.terraindiffusionmc.explorer;

import com.github.xandergos.terraindiffusionmc.biome.BiomePalette;
import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.infinitetensor.FloatTensor;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeClassifier;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.WorldPipelineModelConfig;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Embedded terrain explorer HTTP server. Java port of
 * terrain_diffusion/inference/explorer/server.py.
 *
 * <p>Bound to 127.0.0.1 only. All pipeline calls are routed through
 * LocalTerrainProvider's inference thread for thread safety.
 */
public final class ExplorerServer {

    private static final Logger LOG = LoggerFactory.getLogger(ExplorerServer.class);
    private static final Gson GSON = new Gson();

    private static final String[] CHANNEL_NAMES = {"Elev", "p5", "Temp", "T std", "Precip", "Precip CV"};
    private static final float NATIVE_RESOLUTION = WorldPipelineModelConfig.nativeResolution();

    private static volatile HttpServer SERVER;
    private static volatile int SERVER_PORT = -1;

    private ExplorerServer() {}

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Start the server if not already running. Returns the port.
     */
    public static synchronized int startIfNotRunning() throws IOException {
        if (SERVER != null) return SERVER_PORT;
        int port = TerrainDiffusionConfig.explorerPort();
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", port);
        HttpServer server = HttpServer.create(addr, 0);
        server.createContext("/", ExplorerServer::handleRoot);
        server.createContext("/api/status", ExplorerServer::handleStatus);
        server.createContext("/api/seed", ExplorerServer::handleSeed);
        server.createContext("/api/new_seed", ExplorerServer::handleNewSeed);
        server.createContext("/api/coarse.png", ExplorerServer::handleCoarsePng);
        server.createContext("/api/coarse_data.json", ExplorerServer::handleCoarseData);
        server.createContext("/api/coarse_stats", ExplorerServer::handleCoarseStats);
        server.createContext("/api/detail.png", ExplorerServer::handleDetailPng);
        server.createContext("/api/detail_raw", ExplorerServer::handleDetailRaw);
        // Single-thread executor matches Python's threaded=False
        server.setExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "terrain-explorer-http");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        SERVER = server;
        SERVER_PORT = port;
        LOG.info("Terrain explorer started at http://127.0.0.1:{}", port);
        return port;
    }

    public static synchronized void stop() {
        if (SERVER != null) {
            SERVER.stop(0);
            SERVER = null;
            SERVER_PORT = -1;
            LOG.info("Terrain explorer stopped.");
        }
    }

    public static boolean isRunning() {
        return SERVER != null;
    }

    public static int getPort() {
        return SERVER_PORT;
    }

    // =========================================================================
    // Handlers — direct port of server.py routes
    // =========================================================================

    /** GET / → serve index.html */
    private static void handleRoot(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { send405(ex); return; }
        try (InputStream in = ExplorerServer.class.getResourceAsStream(
                "/assets/terrain-diffusion-mc/explorer/index.html")) {
            if (in == null) {
                sendError(ex, 404, "index.html not found");
                return;
            }
            byte[] body = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
        } finally {
            ex.close();
        }
    }

    /** GET /api/status → {seed, channels, native_resolution, scale} */
    private static void handleStatus(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { send405(ex); return; }
        try {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("seed", Long.toUnsignedString(LocalTerrainProvider.getSeed()));
            resp.put("channels", Arrays.asList(CHANNEL_NAMES));
            resp.put("native_resolution", NATIVE_RESOLUTION);
            resp.put("scale", WorldScaleManager.getCurrentScale());
            sendJson(ex, 200, resp);
        } catch (Exception e) {
            sendError(ex, 500, e.getMessage());
        }
    }

    /** POST /api/seed body={seed:int} → {seed} */
    private static void handleSeed(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) { send405(ex); return; }
        try {
            String body = readBody(ex, 1024);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = GSON.fromJson(body, Map.class);
            if (!data.containsKey("seed")) { sendError(ex, 400, "seed required"); return; }
            long newSeed = ((Number) data.get("seed")).longValue();
            LocalTerrainProvider.changeSeedFromExplorer(newSeed);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("seed", Long.toUnsignedString(LocalTerrainProvider.getSeed()));
            sendJson(ex, 200, resp);
        } catch (Exception e) {
            sendError(ex, 400, e.getMessage());
        }
    }

    /** POST /api/new_seed → {seed} */
    private static void handleNewSeed(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) { send405(ex); return; }
        try {
            long newSeed = LocalTerrainProvider.generateRandomSeedFromExplorer();
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("seed", Long.toUnsignedString(newSeed));
            sendJson(ex, 200, resp);
        } catch (Exception e) {
            sendError(ex, 400, e.getMessage());
        }
    }

    /**
     * GET /api/coarse.png — port of coarse_png() + _coarse_channel().
     * Query params: channel, ci0, ci1, cj0, cj1, ch{0,2,3,4,5}_min/max
     * Response headers: X-Vmin, X-Vmax
     */
    private static void handleCoarsePng(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { send405(ex); return; }
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI());
            int channel = getInt(q, "channel", 0);
            int ci0 = getInt(q, "ci0", -50), ci1 = getInt(q, "ci1", 50);
            int cj0 = getInt(q, "cj0", -50), cj1 = getInt(q, "cj1", 50);

            float[] data = coarseChannel(ci0, ci1, cj0, cj1, channel);
            int H = ci1 - ci0, W = cj1 - cj0;

            // Precipitation: log1p(max(v,0)) before normalizing (matches Python)
            float[] display = data.clone();
            if (channel == 4) {
                for (int i = 0; i < display.length; i++)
                    display[i] = (float) Math.log1p(Math.max(0f, display[i]));
            }
            float vmin = nanMin(display), vmax = nanMax(display);
            if (vmax == vmin) vmax = vmin + 1f;

            // Viridis colormap
            float[][] rgba = new float[4][H * W];
            for (int i = 0; i < H * W; i++) {
                float t = (display[i] - vmin) / (vmax - vmin);
                float[] rgb = Colormaps.viridis(clamp01(t));
                rgba[0][i] = rgb[0]; rgba[1][i] = rgb[1]; rgba[2][i] = rgb[2]; rgba[3][i] = 1f;
            }

            // Optional filter: dim non-matching pixels to 30% (matches Python rgba[~mask, :3] *= 0.3)
            int[] filterChs = {0, 2, 3, 4, 5};
            boolean filterActive = false;
            for (int ch : filterChs) {
                if (q.containsKey("ch" + ch + "_min") || q.containsKey("ch" + ch + "_max")) {
                    filterActive = true; break;
                }
            }
            if (filterActive) {
                boolean[] mask = new boolean[H * W];
                Arrays.fill(mask, true);
                for (int ch : filterChs) {
                    Float lo = getFloat(q, "ch" + ch + "_min");
                    Float hi = getFloat(q, "ch" + ch + "_max");
                    if (lo == null && hi == null) continue;
                    float[] chData = coarseChannel(ci0, ci1, cj0, cj1, ch);
                    for (int i = 0; i < H * W; i++) {
                        if (lo != null && chData[i] < lo) mask[i] = false;
                        if (hi != null && chData[i] > hi) mask[i] = false;
                    }
                }
                for (int i = 0; i < H * W; i++) {
                    if (!mask[i]) {
                        rgba[0][i] *= 0.3f; rgba[1][i] *= 0.3f; rgba[2][i] *= 0.3f;
                    }
                }
            }

            byte[] png = toPng(rgba, H, W);
            ex.getResponseHeaders().set("Content-Type", "image/png");
            ex.getResponseHeaders().set("X-Vmin", String.format("%.3f", vmin));
            ex.getResponseHeaders().set("X-Vmax", String.format("%.3f", vmax));
            ex.getResponseHeaders().set("Access-Control-Expose-Headers", "X-Vmin, X-Vmax");
            ex.sendResponseHeaders(200, png.length);
            ex.getResponseBody().write(png);
        } catch (Exception e) {
            LOG.error("coarse.png error", e);
            sendError(ex, 400, e.getMessage());
        } finally {
            ex.close();
        }
    }

    /**
     * GET /api/coarse_data.json — port of coarse_data().
     * Returns all 6 channel values as 2D arrays for client-side hover.
     */
    private static void handleCoarseData(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { send405(ex); return; }
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI());
            int ci0 = getInt(q, "ci0", -50), ci1 = getInt(q, "ci1", 50);
            int cj0 = getInt(q, "cj0", -50), cj1 = getInt(q, "cj1", 50);
            int H = ci1 - ci0, W = cj1 - cj0;

            Map<String, Object> channels = new LinkedHashMap<>();
            for (int ch = 0; ch < CHANNEL_NAMES.length; ch++) {
                float[] flat = coarseChannel(ci0, ci1, cj0, cj1, ch);
                channels.put(CHANNEL_NAMES[ch], roundedGrid(flat, H, W, 2));
            }
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ci0", ci0); resp.put("ci1", ci1);
            resp.put("cj0", cj0); resp.put("cj1", cj1);
            resp.put("channels", channels);
            sendJson(ex, 200, resp);
        } catch (Exception e) {
            sendError(ex, 400, e.getMessage());
        }
    }

    /** GET /api/coarse_stats — port of coarse_stats(). */
    private static void handleCoarseStats(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { send405(ex); return; }
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI());
            int ci0 = getInt(q, "ci0", -50), ci1 = getInt(q, "ci1", 50);
            int cj0 = getInt(q, "cj0", -50), cj1 = getInt(q, "cj1", 50);

            Map<String, Object> stats = new LinkedHashMap<>();
            for (int ch = 0; ch < CHANNEL_NAMES.length; ch++) {
                float[] data = coarseChannel(ci0, ci1, cj0, cj1, ch);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", CHANNEL_NAMES[ch]);
                entry.put("min", round3(nanMin(data)));
                entry.put("max", round3(nanMax(data)));
                stats.put(String.valueOf(ch), entry);
            }
            sendJson(ex, 200, stats);
        } catch (Exception e) {
            sendError(ex, 400, e.getMessage());
        }
    }

    /**
     * GET /api/detail.png — port of detail_png().
     * Query params: ci, cj, detail_size, pan_i, pan_j, mode
     */
    private static void handleDetailPng(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { send405(ex); return; }
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI());
            int ci         = getInt(q, "ci", 0);
            int cj         = getInt(q, "cj", 0);
            int detailSize = getInt(q, "detail_size", 1024);
            int panI       = getInt(q, "pan_i", 0);
            int panJ       = getInt(q, "pan_j", 0);
            String mode    = q.getOrDefault("mode", "relief");

            int centerI = ci * 256 + panI;
            int centerJ = cj * 256 + panJ;
            int half    = detailSize / 2;
            int H = detailSize, W = detailSize;

            float[][] rgba;
            if (mode.equals("biome")) {
                DetailBiomeData biomeData = loadDetailBiomeData(
                        centerI - half, centerJ - half, centerI + half, centerJ + half);
                rgba = applyBiomeColormap(biomeData.biomeIds, H, W);
            } else {
                float[][] out = LocalTerrainProvider.getPipelineData(
                        centerI - half, centerJ - half, centerI + half, centerJ + half,
                        mode.equals("temperature"));
                float[] elevFlat  = out[0];
                float[] climate   = out[1];

                if (mode.equals("elevation")) {
                    float vmin = nanMin(elevFlat), vmax = nanMax(elevFlat);
                    if (vmax == vmin) vmax = vmin + 1f;
                    rgba = applyColormap1D(elevFlat, H, W, vmin, vmax, "terrain");
                } else if (mode.equals("temperature") && climate != null) {
                    // climate[0] = temperature channel (H*W floats)
                    float[] temp = Arrays.copyOfRange(climate, 0, H * W);
                    float vmin = nanMin(temp), vmax = nanMax(temp);
                    if (vmax == vmin) vmax = vmin + 1f;
                    rgba = applyColormap1D(temp, H, W, vmin, vmax, "rdbu_r");
                } else {
                    // relief mode (default)
                    float[][] reliefRgb = ReliefMap.getReliefMap(elevFlat, H, W, 90.0);
                    rgba = new float[4][H * W];
                    for (int i = 0; i < H * W; i++) {
                        rgba[0][i] = reliefRgb[0][i];
                        rgba[1][i] = reliefRgb[1][i];
                        rgba[2][i] = reliefRgb[2][i];
                        rgba[3][i] = 1f;
                    }
                }
            }

            byte[] png = toPng(rgba, H, W);
            ex.getResponseHeaders().set("Content-Type", "image/png");
            ex.sendResponseHeaders(200, png.length);
            ex.getResponseBody().write(png);
        } catch (Exception e) {
            LOG.error("detail.png error", e);
            sendError(ex, 400, e.getMessage());
        } finally {
            ex.close();
        }
    }

    /**
     * GET /api/detail_raw — port of detail_raw().
     * Binary: int16-LE elevation (H*W*2 bytes) + float32-LE temperature (H*W*4 bytes).
     * Headers: X-Height, X-Width, X-Has-Temp.
     */
    private static void handleDetailRaw(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { send405(ex); return; }
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI());
            int ci         = getInt(q, "ci", 0);
            int cj         = getInt(q, "cj", 0);
            int detailSize = getInt(q, "detail_size", 1024);
            int panI       = getInt(q, "pan_i", 0);
            int panJ       = getInt(q, "pan_j", 0);
            String mode    = q.getOrDefault("mode", "relief");

            int centerI = ci * 256 + panI;
            int centerJ = cj * 256 + panJ;
            int half    = detailSize / 2;
            int H = detailSize, W = detailSize;

            float[] elevFlat;
            float[] climate;
            short[] biomeIds = null;
            if (mode.equals("biome")) {
                DetailBiomeData biomeData = loadDetailBiomeData(
                        centerI - half, centerJ - half, centerI + half, centerJ + half);
                elevFlat = biomeData.elev;
                climate = biomeData.climate;
                biomeIds = biomeData.biomeIds;
            } else {
                float[][] out = LocalTerrainProvider.getPipelineData(
                        centerI - half, centerJ - half, centerI + half, centerJ + half, true);
                elevFlat = out[0];
                climate  = out[1];
            }

            // Elevation → int16 LE (matching Python: clip(floor(elev), -32768, 32767).astype('<i2'))
            ByteBuffer elevBuf = ByteBuffer.allocate(H * W * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (float e : elevFlat) {
                short s = (short) Math.max(-32768, Math.min(32767, (int) Math.floor(e)));
                elevBuf.putShort(s);
            }

            boolean hasTemp = climate != null;
            boolean hasBiome = biomeIds != null;
            ByteBuffer tempBuf = null;
            ByteBuffer biomeBuf = null;
            int payloadLength = elevBuf.capacity();
            if (hasTemp) {
                // Temperature = climate[0..H*W] as float32 LE
                tempBuf = ByteBuffer.allocate(H * W * 4).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < H * W; i++) tempBuf.putFloat(climate[i]);
                payloadLength += tempBuf.capacity();
            }
            if (hasBiome) {
                biomeBuf = ByteBuffer.allocate(H * W * 2).order(ByteOrder.LITTLE_ENDIAN);
                for (short biomeId : biomeIds) biomeBuf.putShort(biomeId);
                payloadLength += biomeBuf.capacity();
            }

            byte[] payload = new byte[payloadLength];
            int offset = 0;
            System.arraycopy(elevBuf.array(), 0, payload, offset, elevBuf.capacity());
            offset += elevBuf.capacity();
            if (hasTemp) {
                System.arraycopy(tempBuf.array(), 0, payload, offset, tempBuf.capacity());
                offset += tempBuf.capacity();
            }
            if (hasBiome) {
                System.arraycopy(biomeBuf.array(), 0, payload, offset, biomeBuf.capacity());
            }

            ex.getResponseHeaders().set("Content-Type", "application/octet-stream");
            ex.getResponseHeaders().set("X-Height", String.valueOf(H));
            ex.getResponseHeaders().set("X-Width", String.valueOf(W));
            ex.getResponseHeaders().set("X-Has-Temp", hasTemp ? "1" : "0");
            ex.getResponseHeaders().set("X-Has-Biome", hasBiome ? "1" : "0");
            ex.getResponseHeaders().set("Access-Control-Expose-Headers", "X-Height, X-Width, X-Has-Temp, X-Has-Biome");
            ex.sendResponseHeaders(200, payload.length);
            ex.getResponseBody().write(payload);
        } catch (Exception e) {
            LOG.error("detail_raw error", e);
            sendError(ex, 400, e.getMessage());
        } finally {
            ex.close();
        }
    }

    private record DetailBiomeData(float[] elev, float[] climate, short[] biomeIds) {}

    private static DetailBiomeData loadDetailBiomeData(int i1, int j1, int i2, int j2) throws Exception {
        int H = i2 - i1;
        int W = j2 - j1;
        int pH = H + 2;
        int pW = W + 2;

        float[][] out = LocalTerrainProvider.getPipelineData(i1 - 1, j1 - 1, i2 + 1, j2 + 1, true);
        float[] elevPadded = out[0];
        float[] climatePadded = out[1];
        float[] elev = new float[H * W];
        float[] climate = climatePadded != null ? new float[4 * H * W] : null;

        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int dst = r * W + c;
                int src = (r + 1) * pW + (c + 1);
                elev[dst] = elevPadded[src];
            }
        }

        if (climate != null) {
            for (int ch = 0; ch < 4; ch++) {
                int dstBase = ch * H * W;
                int srcBase = ch * pH * pW;
                for (int r = 0; r < H; r++) {
                    int srcRow = srcBase + (r + 1) * pW + 1;
                    int dstRow = dstBase + r * W;
                    System.arraycopy(climatePadded, srcRow, climate, dstRow, W);
                }
            }
        }

        short[] biomeIds = BiomeClassifier.classify(elev, climate, i1, j1, elevPadded, H, W, NATIVE_RESOLUTION);
        return new DetailBiomeData(elev, climate, biomeIds);
    }

    // =========================================================================
    // Coarse channel helper — port of _coarse_channel() in server.py
    // =========================================================================

    /**
     * Return the given channel of the coarse map in real units.
     * Channels 0 and 1: undo signed-sqrt (sign(v) * v^2).
     */
    private static float[] coarseChannel(int ci0, int ci1, int cj0, int cj1, int channel) throws Exception {
        FloatTensor slice = LocalTerrainProvider.getPipelineCoarse(ci0, cj0, ci1, cj1);
        int H = ci1 - ci0, W = cj1 - cj0;
        float[] result = new float[H * W];
        for (int i = 0; i < H * W; i++) {
            float w   = slice.data[6 * H * W + i];
            float raw = (w > 1e-8f) ? slice.data[channel * H * W + i] / w : 0f;
            // Channels 0 (elev) and 1 (p5): signed-sqrt → real units via sign(v)*v^2
            result[i] = (channel <= 1) ? (float) (Math.signum(raw) * raw * raw) : raw;
        }
        return result;
    }

    // =========================================================================
    // PNG rendering
    // =========================================================================

    /** Encode RGBA channels (float[4][H*W]) to a PNG byte array. */
    private static byte[] toPng(float[][] rgba, int H, int W) throws IOException {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int idx = r * W + c;
                int ri = (int) (clamp01(rgba[0][idx]) * 255f + 0.5f);
                int gi = (int) (clamp01(rgba[1][idx]) * 255f + 0.5f);
                int bi = (int) (clamp01(rgba[2][idx]) * 255f + 0.5f);
                int ai = (int) (clamp01(rgba[3][idx]) * 255f + 0.5f);
                img.setRGB(c, r, (ai << 24) | (ri << 16) | (gi << 8) | bi);
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    private static float[][] applyColormap1D(float[] data, int H, int W, float vmin, float vmax, String cmap) {
        float[][] rgba = new float[4][H * W];
        for (int i = 0; i < H * W; i++) {
            float t = (data[i] - vmin) / (vmax - vmin);
            float[] rgb;
            switch (cmap) {
                case "terrain": rgb = Colormaps.terrain(clamp01(t)); break;
                case "rdbu_r":  rgb = Colormaps.rdBuR(clamp01(t));   break;
                default:        rgb = Colormaps.viridis(clamp01(t)); break;
            }
            rgba[0][i] = rgb[0]; rgba[1][i] = rgb[1]; rgba[2][i] = rgb[2]; rgba[3][i] = 1f;
        }
        return rgba;
    }


    private static float[][] applyBiomeColormap(short[] biomeIds, int H, int W) {
        float[][] rgba = new float[4][H * W];
        for (int i = 0; i < H * W; i++) {
            int rgb = biomeRgb(biomeIds[i]);
            rgba[0][i] = ((rgb >> 16) & 0xFF) / 255f;
            rgba[1][i] = ((rgb >> 8) & 0xFF) / 255f;
            rgba[2][i] = (rgb & 0xFF) / 255f;
            rgba[3][i] = 1f;
        }
        return rgba;
    }

    private static int biomeRgb(short id) {
        switch (id) {
            case BiomePalette.THE_VOID: return 0x000000;
            case BiomePalette.SUNFLOWER_PLAINS: return 0xB7D85B;
            case BiomePalette.SNOWY_PLAINS: return 0xFFFFFF;
            case BiomePalette.ICE_SPIKES: return 0xD8F4FF;
            case BiomePalette.DESERT: return 0xFADE55;
            case BiomePalette.SWAMP: return 0x4C763C;
            case BiomePalette.MANGROVE_SWAMP: return 0x3C6B4A;
            case BiomePalette.FOREST: return 0x056621;
            case BiomePalette.FLOWER_FOREST: return 0x2D8E49;
            case BiomePalette.BIRCH_FOREST: return 0x6DA06B;
            case BiomePalette.DARK_FOREST: return 0x40511A;
            case BiomePalette.OLD_GROWTH_BIRCH_FOREST: return 0x589C6C;
            case BiomePalette.OLD_GROWTH_PINE_TAIGA: return 0x596651;
            case BiomePalette.OLD_GROWTH_SPRUCE_TAIGA: return 0x5B6E55;
            case BiomePalette.TAIGA: return 0x0B6659;
            case BiomePalette.SNOWY_TAIGA: return 0xB4DCDC;
            case BiomePalette.SAVANNA: return 0xBDB25F;
            case BiomePalette.SAVANNA_PLATEAU: return 0xA79D64;
            case BiomePalette.WINDSWEPT_HILLS: return 0x606060;
            case BiomePalette.WINDSWEPT_GRAVELLY_HILLS: return 0x888888;
            case BiomePalette.WINDSWEPT_FOREST: return 0x507050;
            case BiomePalette.WINDSWEPT_SAVANNA: return 0xA9A25A;
            case BiomePalette.JUNGLE: return 0x537B09;
            case BiomePalette.SPARSE_JUNGLE: return 0x628B17;
            case BiomePalette.BAMBOO_JUNGLE: return 0x768E14;
            case BiomePalette.BADLANDS: return 0xD94515;
            case BiomePalette.WOODED_BADLANDS: return 0xB36C31;
            case BiomePalette.ERODED_BADLANDS: return 0xC65A24;
            case BiomePalette.MEADOW: return 0x60A85F;
            case BiomePalette.CHERRY_GROVE: return 0xF7B4C8;
            case BiomePalette.GROVE: return 0x85A7A4;
            case BiomePalette.SNOWY_SLOPES: return 0xE5F1F1;
            case BiomePalette.FROZEN_PEAKS: return 0xA7D8D8;
            case BiomePalette.JAGGED_PEAKS: return 0x999999;
            case BiomePalette.STONY_PEAKS: return 0x777777;
            case BiomePalette.FROZEN_RIVER: return 0xA0A0FF;
            case BiomePalette.RIVER: return 0x0000FF;
            case BiomePalette.BEACH: return 0xFADE55;
            case BiomePalette.SNOWY_BEACH: return 0xFAF0C0;
            case BiomePalette.STONY_SHORE: return 0xA2A284;
            case BiomePalette.WARM_OCEAN: return 0x0000AC;
            case BiomePalette.LUKEWARM_OCEAN: return 0x000070;
            case BiomePalette.DEEP_LUKEWARM_OCEAN: return 0x000040;
            case BiomePalette.OCEAN: return 0x0000A8;
            case BiomePalette.DEEP_OCEAN: return 0x000030;
            case BiomePalette.COLD_OCEAN: return 0x202070;
            case BiomePalette.DEEP_COLD_OCEAN: return 0x202038;
            case BiomePalette.FROZEN_OCEAN: return 0x7070D6;
            case BiomePalette.DEEP_FROZEN_OCEAN: return 0x404090;
            case BiomePalette.MUSHROOM_FIELDS: return 0xFF00FF;
            case BiomePalette.DRIPSTONE_CAVES: return 0x8C7A65;
            case BiomePalette.LUSH_CAVES: return 0x58B84A;
            case BiomePalette.DEEP_DARK: return 0x07131A;
            case BiomePalette.NETHER_WASTES: return 0xBF3B2B;
            case BiomePalette.WARPED_FOREST: return 0x2B7F8C;
            case BiomePalette.CRIMSON_FOREST: return 0x8C1F32;
            case BiomePalette.SOUL_SAND_VALLEY: return 0x5A463A;
            case BiomePalette.BASALT_DELTAS: return 0x403A3A;
            case BiomePalette.THE_END: return 0xF5F0B0;
            case BiomePalette.END_HIGHLANDS: return 0xD6D184;
            case BiomePalette.END_MIDLANDS: return 0xC9C477;
            case BiomePalette.SMALL_END_ISLANDS: return 0xB8B36B;
            case BiomePalette.END_BARRENS: return 0x9E9A5D;
            case BiomePalette.PALE_GARDEN: return 0xC7C7B0;
            case BiomePalette.FOREST_SPARSE: return 0x4E8E4E;
            case BiomePalette.TAIGA_SPARSE: return 0x4D6B61;
            case BiomePalette.SNOWY_TAIGA_SPARSE: return 0xC8E8E8;
            case BiomePalette.PLAINS:
            default: return 0x8DB360;
        }
    }

    // =========================================================================
    // HTTP utilities
    // =========================================================================

    private static void sendJson(HttpExchange ex, int status, Object obj) throws IOException {
        byte[] body = GSON.toJson(obj).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        ex.close();
    }

    private static void sendError(HttpExchange ex, int status, String msg) throws IOException {
        Map<String, String> err = new HashMap<>();
        err.put("error", msg != null ? msg : "unknown error");
        sendJson(ex, status, err);
    }

    private static void send405(HttpExchange ex) throws IOException {
        sendError(ex, 405, "Method Not Allowed");
    }

    private static String readBody(HttpExchange ex, int maxBytes) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] buf = in.readNBytes(maxBytes);
            return new String(buf, StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parseQuery(URI uri) {
        Map<String, String> map = new HashMap<>();
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isEmpty()) return map;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq >= 0) {
                map.put(pair.substring(0, eq), pair.substring(eq + 1));
            } else {
                map.put(pair, "");
            }
        }
        return map;
    }

    // =========================================================================
    // Math utilities
    // =========================================================================

    private static float nanMin(float[] arr) {
        float min = Float.MAX_VALUE;
        for (float v : arr) if (!Float.isNaN(v) && v < min) min = v;
        return min == Float.MAX_VALUE ? 0f : min;
    }

    private static float nanMax(float[] arr) {
        float max = -Float.MAX_VALUE;
        for (float v : arr) if (!Float.isNaN(v) && v > max) max = v;
        return max == -Float.MAX_VALUE ? 0f : max;
    }

    private static float clamp01(float v) {
        return Math.min(1f, Math.max(0f, v));
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    /** Rounded 2-D list for coarse_data JSON (np.round equivalent). */
    private static List<List<Double>> roundedGrid(float[] flat, int H, int W, int decimals) {
        double factor = Math.pow(10, decimals);
        List<List<Double>> grid = new ArrayList<>(H);
        for (int r = 0; r < H; r++) {
            List<Double> row = new ArrayList<>(W);
            for (int c = 0; c < W; c++) {
                row.add(Math.round(flat[r * W + c] * factor) / factor);
            }
            grid.add(row);
        }
        return grid;
    }

    private static int getInt(Map<String, String> q, String key, int def) {
        String v = q.get(key);
        if (v == null) return def;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return def; }
    }

    private static Float getFloat(Map<String, String> q, String key) {
        String v = q.get(key);
        if (v == null) return null;
        try { return Float.parseFloat(v); } catch (NumberFormatException e) { return null; }
    }
}
