/*
 *
 * The MIT License
 *
 * Copyright (c) 2025, Gong Yi.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package io.jenkins.plugins.mcp.server.extensions;

import hudson.Extension;
import io.jenkins.plugins.mcp.server.McpServerExtension;
import io.jenkins.plugins.mcp.server.annotation.Tool;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * MCP Server Extension that provides tools for retrieving JVM metrics and memory utilization.
 */
@Extension
@Slf4j
public class JvmMetricsExtension implements McpServerExtension {

    /**
     * Memory metrics record for serialization.
     */
    public record MemoryMetrics(
            long usedBytes,
            long maxBytes,
            long committedBytes,
            long freeBytes,
            double usedPercentage,
            String usedMB,
            String maxMB,
            String freeMB) {}

    /**
     * Memory pool metrics record for serialization.
     */
    public record MemoryPoolMetrics(
            String name, String type, long usedBytes, long maxBytes, double usedPercentage, String usedMB, String maxMB) {
    }

    @Tool(
            description =
                    "Get JVM heap memory utilization and statistics for the Jenkins instance. Returns current usage, maximum heap, free memory, and usage percentage.",
            annotations = @Tool.Annotations(destructiveHint = false, readOnlyHint = true))
    public Map<String, Object> getJvmHeapUtilization() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();

        long used = heapMemoryUsage.getUsed();
        long max = heapMemoryUsage.getMax();
        long committed = heapMemoryUsage.getCommitted();
        long free = max - used;
        double usedPercentage = max > 0 ? (double) used / max * 100 : 0;

        MemoryMetrics heapMetrics = new MemoryMetrics(
                used,
                max,
                committed,
                free,
                usedPercentage,
                formatBytes(used),
                formatBytes(max),
                formatBytes(free));

        Map<String, Object> result = new HashMap<>();
        result.put("heap", heapMetrics);
        result.put("timestamp", System.currentTimeMillis());

        return result;
    }

    @Tool(
            description =
                    "Get comprehensive JVM memory metrics including heap, non-heap, and individual memory pools. "
                            + "Provides detailed breakdown of Eden Space, Old Gen, Survivor Space, Metaspace, and other memory areas.",
            annotations = @Tool.Annotations(destructiveHint = false, readOnlyHint = true))
    public Map<String, Object> getJvmMemoryMetrics() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

        // Heap Memory
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        long heapUsed = heapMemoryUsage.getUsed();
        long heapMax = heapMemoryUsage.getMax();
        long heapCommitted = heapMemoryUsage.getCommitted();
        long heapFree = heapMax - heapUsed;
        double heapUsedPercentage = heapMax > 0 ? (double) heapUsed / heapMax * 100 : 0;

        MemoryMetrics heapMetrics = new MemoryMetrics(
                heapUsed,
                heapMax,
                heapCommitted,
                heapFree,
                heapUsedPercentage,
                formatBytes(heapUsed),
                formatBytes(heapMax),
                formatBytes(heapFree));

        // Non-Heap Memory (Metaspace, Code Cache, etc.)
        MemoryUsage nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage();
        long nonHeapUsed = nonHeapMemoryUsage.getUsed();
        long nonHeapMax = nonHeapMemoryUsage.getMax();
        long nonHeapCommitted = nonHeapMemoryUsage.getCommitted();
        long nonHeapFree = nonHeapMax > 0 ? nonHeapMax - nonHeapUsed : -1;
        double nonHeapUsedPercentage = nonHeapMax > 0 ? (double) nonHeapUsed / nonHeapMax * 100 : 0;

        MemoryMetrics nonHeapMetrics = new MemoryMetrics(
                nonHeapUsed,
                nonHeapMax,
                nonHeapCommitted,
                nonHeapFree,
                nonHeapUsedPercentage,
                formatBytes(nonHeapUsed),
                nonHeapMax > 0 ? formatBytes(nonHeapMax) : "undefined",
                nonHeapFree > 0 ? formatBytes(nonHeapFree) : "undefined");

        // Memory Pools (Eden, Old Gen, Survivor, Metaspace, etc.)
        List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
        List<MemoryPoolMetrics> memoryPools = memoryPoolMXBeans.stream()
                .map(pool -> {
                    MemoryUsage usage = pool.getUsage();
                    long poolUsed = usage.getUsed();
                    long poolMax = usage.getMax();
                    double poolUsedPercentage = poolMax > 0 ? (double) poolUsed / poolMax * 100 : 0;

                    return new MemoryPoolMetrics(
                            pool.getName(),
                            pool.getType().toString(),
                            poolUsed,
                            poolMax,
                            poolUsedPercentage,
                            formatBytes(poolUsed),
                            poolMax > 0 ? formatBytes(poolMax) : "undefined");
                })
                .collect(Collectors.toList());

        // Runtime Info
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        long usedMemory = totalMemory - freeMemory;

        Map<String, Object> runtimeMetrics = new HashMap<>();
        runtimeMetrics.put("totalMemoryBytes", totalMemory);
        runtimeMetrics.put("freeMemoryBytes", freeMemory);
        runtimeMetrics.put("maxMemoryBytes", maxMemory);
        runtimeMetrics.put("usedMemoryBytes", usedMemory);
        runtimeMetrics.put("totalMemory", formatBytes(totalMemory));
        runtimeMetrics.put("freeMemory", formatBytes(freeMemory));
        runtimeMetrics.put("maxMemory", formatBytes(maxMemory));
        runtimeMetrics.put("usedMemory", formatBytes(usedMemory));
        runtimeMetrics.put("availableProcessors", runtime.availableProcessors());

        Map<String, Object> result = new HashMap<>();
        result.put("heap", heapMetrics);
        result.put("nonHeap", nonHeapMetrics);
        result.put("memoryPools", memoryPools);
        result.put("runtime", runtimeMetrics);
        result.put("timestamp", System.currentTimeMillis());

        return result;
    }

    @Tool(
            description =
                    "Trigger garbage collection and return memory metrics before and after GC. "
                            + "WARNING: This is a destructive operation that may impact Jenkins performance temporarily.",
            annotations = @Tool.Annotations(destructiveHint = true, readOnlyHint = false))
    public Map<String, Object> triggerGarbageCollection() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

        // Get memory before GC
        MemoryUsage heapBeforeGC = memoryMXBean.getHeapMemoryUsage();
        long usedBeforeGC = heapBeforeGC.getUsed();

        // Trigger GC
        long startTime = System.currentTimeMillis();
        System.gc();
        long gcDuration = System.currentTimeMillis() - startTime;

        // Get memory after GC
        MemoryUsage heapAfterGC = memoryMXBean.getHeapMemoryUsage();
        long usedAfterGC = heapAfterGC.getUsed();
        long freedMemory = usedBeforeGC - usedAfterGC;

        Map<String, Object> result = new HashMap<>();
        result.put("beforeGC", Map.of(
                "usedBytes", usedBeforeGC,
                "used", formatBytes(usedBeforeGC)));
        result.put("afterGC", Map.of(
                "usedBytes", usedAfterGC,
                "used", formatBytes(usedAfterGC)));
        result.put("freedMemoryBytes", freedMemory);
        result.put("freedMemory", formatBytes(freedMemory));
        result.put("gcDurationMs", gcDuration);
        result.put("timestamp", System.currentTimeMillis());

        return result;
    }

    /**
     * Format bytes to human-readable format (MB, GB)
     */
    private String formatBytes(long bytes) {
        if (bytes < 0) {
            return "undefined";
        }
        double mb = bytes / (1024.0 * 1024.0);
        if (mb < 1024) {
            return String.format("%.2f MB", mb);
        }
        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }
}
