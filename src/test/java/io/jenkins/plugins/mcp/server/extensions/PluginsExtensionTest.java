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
import java.util.HashMap;
import java.util.Map;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class PluginsExtensionTest {

    @McpClientTest
    void testMcpToolCallGetInstalledPlugins(
            JenkinsRule jenkins, JenkinsMcpClientBuilder jenkinsMcpClientBuilder) {
        try (var client = jenkinsMcpClientBuilder.jenkins(jenkins).build()) {
            // Test with default parameters
            McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("getInstalledPlugins", Map.of());

            var response = client.callTool(request);
            assertThat(response.isError()).isFalse();
            assertThat(response.content()).isNotEmpty();
            assertThat(response.content().get(0).type()).isEqualTo("text");

            // Verify the response contains plugin information
            response.content().forEach(content -> {
                assertThat(content).isInstanceOfSatisfying(McpSchema.TextContent.class, textContent -> {
                    assertThat(textContent.type()).isEqualTo("text");

                    ObjectMapper objectMapper = new ObjectMapper();
                    try {
                        var contentMap = objectMapper.readValue(textContent.text(), Map.class);
                        // Verify plugin info fields exist
                        assertThat(contentMap).containsKeys("shortName", "displayName", "version", "active", "enabled");
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                });
            });
        }
    }

    @McpClientTest
    void testMcpToolCallGetInstalledPluginsWithPagination(
            JenkinsRule jenkins, JenkinsMcpClientBuilder jenkinsMcpClientBuilder) {
        try (var client = jenkinsMcpClientBuilder.jenkins(jenkins).build()) {
            // Test with pagination parameters
            Map<String, Object> params = new HashMap<>();
            params.put("skip", 0);
            params.put("limit", 10);

            McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("getInstalledPlugins", params);

            var response = client.callTool(request);
            assertThat(response.isError()).isFalse();
            assertThat(response.content()).isNotEmpty();
            // Should return at most 10 plugins
            assertThat(response.content().size()).isLessThanOrEqualTo(10);
        }
    }

    @McpClientTest
    void testMcpToolCallGetInstalledPluginsActiveOnly(
            JenkinsRule jenkins, JenkinsMcpClientBuilder jenkinsMcpClientBuilder) {
        try (var client = jenkinsMcpClientBuilder.jenkins(jenkins).build()) {
            // Test with activeOnly filter
            Map<String, Object> params = new HashMap<>();
            params.put("activeOnly", true);

            McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("getInstalledPlugins", params);

            var response = client.callTool(request);
            assertThat(response.isError()).isFalse();
            assertThat(response.content()).isNotEmpty();

            // Verify all returned plugins are active
            response.content().forEach(content -> {
                assertThat(content).isInstanceOfSatisfying(McpSchema.TextContent.class, textContent -> {
                    ObjectMapper objectMapper = new ObjectMapper();
                    try {
                        var contentMap = objectMapper.readValue(textContent.text(), Map.class);
                        assertThat(contentMap).extractingByKey("active").isEqualTo(true);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                });
            });
        }
    }

    @McpClientTest
    void testMcpToolCallGetPlugin(JenkinsRule jenkins, JenkinsMcpClientBuilder jenkinsMcpClientBuilder) {
        try (var client = jenkinsMcpClientBuilder.jenkins(jenkins).build()) {
            // Get a specific plugin - we know 'git' plugin is available in tests
            // Actually, let's use a plugin we know exists in the test environment
            var plugins = jenkins.jenkins.getPluginManager().getPlugins();
            assertThat(plugins).isNotEmpty();
            var firstPlugin = plugins.get(0);

            McpSchema.CallToolRequest request =
                    new McpSchema.CallToolRequest("getPlugin", Map.of("pluginShortName", firstPlugin.getShortName()));

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
                            assertThat(contentMap).extractingByKey("shortName").isEqualTo(firstPlugin.getShortName());
                            assertThat(contentMap)
                                    .extractingByKey("displayName")
                                    .isEqualTo(firstPlugin.getDisplayName());
                            assertThat(contentMap).containsKeys("version", "active", "enabled", "hasUpdate", "url");
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    @McpClientTest
    void testMcpToolCallGetPluginNotFound(JenkinsRule jenkins, JenkinsMcpClientBuilder jenkinsMcpClientBuilder) {
        try (var client = jenkinsMcpClientBuilder.jenkins(jenkins).build()) {
            // Test with a non-existent plugin
            McpSchema.CallToolRequest request =
                    new McpSchema.CallToolRequest("getPlugin", Map.of("pluginShortName", "non-existent-plugin"));

            var response = client.callTool(request);
            assertThat(response.isError()).isFalse();
            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0))
                    .isInstanceOfSatisfying(McpSchema.TextContent.class, textContent -> {
                        assertThat(textContent.text()).contains("Result is null");
                    });
        }
    }
}
