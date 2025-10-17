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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jenkins.plugins.mcp.server.junit.JenkinsMcpClientBuilder;
import io.jenkins.plugins.mcp.server.junit.McpClientTest;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class JvmMetricsExtensionTest {

    @McpClientTest
    void testMcpToolCallGetJvmHeapUtilization(JenkinsRule jenkins, JenkinsMcpClientBuilder jenkinsMcpClientBuilder)
            throws JsonProcessingException {
        try (var client = jenkinsMcpClientBuilder.jenkins(jenkins).build()) {
            McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("getJvmHeapUtilization", Map.of());

            var response = client.callTool(request);
            assertThat(response.isError()).isFalse();
            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0).type()).isEqualTo("text");

            assertThat(response.content().get(0))
                    .isInstanceOfSatisfying(McpSchema.TextContent.class, textContent -> {
                        assertThat(textContent.type()).isEqualTo("text");

                        ObjectMapper objectMapper = new ObjectMapper();
                        try {
                            var contentMap = objectMapper.readValue(textContent.text(), Map.class);

                            // Verify heap metrics exist
                            assertThat(contentMap).containsKey("heap");
                            assertThat(contentMap).containsKey("timestamp");

                            Map<String, Object> heap = (Map<String, Object>) contentMap.get("heap");
                            assertThat(heap).containsKeys(
                                    "usedBytes",
                                    "maxBytes",
                                    "committedBytes",
                                    "freeBytes",
                                    "usedPercentage",
                                    "usedMB",
                                    "maxMB",
                                    "freeMB");

                            // Verify values are reasonable
                            assertThat(heap.get("usedBytes")).isInstanceOf(Number.class);
                            assertThat(heap.get("maxBytes")).isInstanceOf(Number.class);
                            assertThat(heap.get("usedPercentage")).isInstanceOf(Number.class);

                            long usedBytes = ((Number) heap.get("usedBytes")).longValue();
                            long maxBytes = ((Number) heap.get("maxBytes")).longValue();
                            assertThat(usedBytes).isGreaterThan(0);
                            assertThat(maxBytes).isGreaterThan(usedBytes);

                            // Verify formatted strings
                            assertThat(heap.get("usedMB")).isInstanceOf(String.class);
                            assertThat(heap.get("maxMB")).isInstanceOf(String.class);
                            assertThat(heap.get("freeMB")).isInstanceOf(String.class);

                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    @McpClientTest
    void testMcpToolCallGetJvmMemoryMetrics(JenkinsRule jenkins, JenkinsMcpClientBuilder jenkinsMcpClientBuilder)
            throws JsonProcessingException {
        try (var client = jenkinsMcpClientBuilder.jenkins(jenkins).build()) {
            McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("getJvmMemoryMetrics", Map.of());

            var response = client.callTool(request);
            assertThat(response.isError()).isFalse();
            assertThat(response.content()).hasSize(1);

            assertThat(response.content().get(0))
                    .isInstanceOfSatisfying(McpSchema.TextContent.class, textContent -> {
                        ObjectMapper objectMapper = new ObjectMapper();
                        try {
                            var contentMap = objectMapper.readValue(textContent.text(), Map.class);

                            // Verify main sections exist
                            assertThat(contentMap).containsKeys("heap", "nonHeap", "memoryPools", "runtime", "timestamp");

                            // Verify heap
                            Map<String, Object> heap = (Map<String, Object>) contentMap.get("heap");
                            assertThat(heap).containsKeys("usedBytes", "maxBytes", "usedPercentage");

                            // Verify nonHeap
                            Map<String, Object> nonHeap = (Map<String, Object>) contentMap.get("nonHeap");
                            assertThat(nonHeap).containsKeys("usedBytes", "committedBytes");

                            // Verify memory pools
                            List<Map<String, Object>> memoryPools =
                                    (List<Map<String, Object>>) contentMap.get("memoryPools");
                            assertThat(memoryPools).isNotEmpty();

                            // Check first memory pool structure
                            Map<String, Object> firstPool = memoryPools.get(0);
                            assertThat(firstPool)
                                    .containsKeys("name", "type", "usedBytes", "maxBytes", "usedPercentage", "usedMB");

                            // Verify runtime metrics
                            Map<String, Object> runtime = (Map<String, Object>) contentMap.get("runtime");
                            assertThat(runtime)
                                    .containsKeys(
                                            "totalMemoryBytes",
                                            "freeMemoryBytes",
                                            "maxMemoryBytes",
                                            "usedMemoryBytes",
                                            "availableProcessors");

                            assertThat(runtime.get("availableProcessors")).isInstanceOf(Number.class);
                            int processors = ((Number) runtime.get("availableProcessors")).intValue();
                            assertThat(processors).isGreaterThan(0);

                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    @McpClientTest
    void testMcpToolCallTriggerGarbageCollection(
            JenkinsRule jenkins, JenkinsMcpClientBuilder jenkinsMcpClientBuilder) throws JsonProcessingException {
        try (var client = jenkinsMcpClientBuilder.jenkins(jenkins).build()) {
            // Allocate some memory to ensure GC has something to collect
            byte[][] memoryHog = new byte[1000][1000];
            for (int i = 0; i < 1000; i++) {
                memoryHog[i] = new byte[1000];
            }
            memoryHog = null; // Make it eligible for GC

            McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("triggerGarbageCollection", Map.of());

            var response = client.callTool(request);
            assertThat(response.isError()).isFalse();
            assertThat(response.content()).hasSize(1);

            assertThat(response.content().get(0))
                    .isInstanceOfSatisfying(McpSchema.TextContent.class, textContent -> {
                        ObjectMapper objectMapper = new ObjectMapper();
                        try {
                            var contentMap = objectMapper.readValue(textContent.text(), Map.class);

                            // Verify GC results
                            assertThat(contentMap).containsKeys("beforeGC", "afterGC", "freedMemoryBytes", "gcDurationMs");

                            Map<String, Object> beforeGC = (Map<String, Object>) contentMap.get("beforeGC");
                            assertThat(beforeGC).containsKeys("usedBytes", "used");

                            Map<String, Object> afterGC = (Map<String, Object>) contentMap.get("afterGC");
                            assertThat(afterGC).containsKeys("usedBytes", "used");

                            // GC duration should be reasonable (less than 10 seconds in test environment)
                            long gcDuration = ((Number) contentMap.get("gcDurationMs")).longValue();
                            assertThat(gcDuration).isGreaterThanOrEqualTo(0).isLessThan(10000);

                            // Freed memory should be a number (could be 0 if nothing was collected)
                            assertThat(contentMap.get("freedMemoryBytes")).isInstanceOf(Number.class);

                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
