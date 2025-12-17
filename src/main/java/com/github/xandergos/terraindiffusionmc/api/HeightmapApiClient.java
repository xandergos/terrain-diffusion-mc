package com.github.xandergos.terraindiffusionmc.api;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class HeightmapApiClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(600);
    private static final int MAX_CACHE_SIZE = 256;
    private static final Map<String, CompletableFuture<HeightmapData>> CACHE =
            java.util.Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CompletableFuture<HeightmapData>> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            });

    private final HttpClient httpClient;
    private final String apiBaseUrl;
    
    public HeightmapApiClient() {
        this(TerrainDiffusionConfig.apiUrl());
    }
    
    public HeightmapApiClient(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(300))
                .build();
    }
    
    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }
    
    public CompletableFuture<HeightmapData> fetchHeightmap(int i1, int j1, int i2, int j2) {
        String cacheKey = i1 + "," + j1 + "," + i2 + "," + j2;
        
        synchronized (CACHE) {
            CompletableFuture<HeightmapData> cached = CACHE.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        
        int scale = TerrainDiffusionConfig.scale();
        float noise = TerrainDiffusionConfig.noise();
        String url = String.format("%s/terrain?i1=%d&j1=%d&i2=%d&j2=%d&scale=%d&noise=%s",
                apiBaseUrl, i1, j1, i2, j2, scale, noise);
            
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        
        CompletableFuture<HeightmapData> future = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("API request failed with status: " + response.statusCode() + 
                                " for URL: " + url);
                    }
                    
                    String heightHeader = response.headers().firstValue("X-Height").orElse("0");
                    String widthHeader = response.headers().firstValue("X-Width").orElse("0");
                    
                    int height = Integer.parseInt(heightHeader);
                    int width = Integer.parseInt(widthHeader);
                    
                    if (width <= 0 || height <= 0) {
                        throw new RuntimeException("Invalid heightmap dimensions: " + width + "x" + height);
                    }
                    
                    byte[] data = response.body();
                    int samples = width * height;
                    int heightmapBytes = samples * Short.BYTES;
                    int expectedBytes = heightmapBytes * 2;

                    if (data.length == heightmapBytes) {
                        short[][] heightmap = parseInt16Array(data, width, height, 0);
                        return new HeightmapData(heightmap, null, width, height);
                    } else if (data.length == expectedBytes) {
                        short[][] heightmap = parseInt16Array(data, width, height, 0);
                        short[][] biomeIds = parseInt16Array(data, width, height, heightmapBytes);
                        return new HeightmapData(heightmap, biomeIds, width, height);
                    } else {
                        throw new RuntimeException("Heightmap data size mismatch. Expected " + expectedBytes +
                                " or " + heightmapBytes + " bytes, got " + data.length);
                    }
                })
                .exceptionally(throwable -> {
                    System.err.println("Failed to fetch heightmap: " + throwable.getMessage());
                    throwable.printStackTrace();
                    synchronized (CACHE) {
                        CACHE.remove(cacheKey);
                    }
                    return null;
                });
        
        synchronized (CACHE) {
            CACHE.put(cacheKey, future);
        }
        return future;
    }
    
    private short[][] parseInt16Array(byte[] data, int width, int height, int offset) {
        ByteBuffer buffer = ByteBuffer.wrap(data, offset, data.length - offset).order(ByteOrder.LITTLE_ENDIAN);
        short[][] result = new short[height][width];
        
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                result[i][j] = buffer.getShort();
            }
        }
        
        return result;
    }
    
    public static class HeightmapData {
        public final short[][] heightmap;
        public final short[][] biomeIds;
        public final int width;
        public final int height;
        
        public HeightmapData(short[][] heightmap, short[][] biomeIds, int width, int height) {
            this.heightmap = heightmap;
            this.biomeIds = biomeIds;
            this.width = width;
            this.height = height;
        }
    }
}

