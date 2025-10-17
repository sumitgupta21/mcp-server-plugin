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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jenkins.plugins.mcp.server.junit.JenkinsMcpClientBuilder;
import io.jenkins.plugins.mcp.server.junit.McpClientTest;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class PluginsListDemo {

    @McpClientTest
    void demonstrateGetInstalledPlugins(JenkinsRule jenkins, JenkinsMcpClientBuilder jenkinsMcpClientBuilder)
            throws Exception {
        try (var client = jenkinsMcpClientBuilder.jenkins(jenkins).build()) {
            System.out.println("\n========================================");
            System.out.println("JENKINS MCP SERVER - INSTALLED PLUGINS");
            System.out.println("========================================\n");

            // Get the list of installed plugins
            McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("getInstalledPlugins", Map.of());
            var response = client.callTool(request);

            ObjectMapper objectMapper = new ObjectMapper();
            int count = 0;

            for (var content : response.content()) {
                if (content instanceof McpSchema.TextContent textContent) {
                    var pluginInfo = objectMapper.readValue(textContent.text(), Map.class);
                    count++;
                    System.out.printf(
                            "%3d. %-30s | %-40s | Version: %-10s | Active: %s%n",
                            count,
                            pluginInfo.get("shortName"),
                            pluginInfo.get("displayName"),
                            pluginInfo.get("version"),
                            pluginInfo.get("active"));
                }
            }

            System.out.println("\n========================================");
            System.out.println("Total Plugins: " + count);
            System.out.println("========================================\n");

            // Also demonstrate getting jobs
            System.out.println("\n========================================");
            System.out.println("JENKINS MCP SERVER - JOBS LIST");
            System.out.println("========================================\n");

            // Create some sample jobs first
            jenkins.createFreeStyleProject("test-job-1");
            jenkins.createFreeStyleProject("test-job-2");
            jenkins.createFreeStyleProject("test-job-3");

            // Get the list of jobs
            McpSchema.CallToolRequest jobsRequest = new McpSchema.CallToolRequest("getJobs", Map.of());
            var jobsResponse = client.callTool(jobsRequest);

            count = 0;
            for (var content : jobsResponse.content()) {
                if (content instanceof McpSchema.TextContent textContent) {
                    var jobInfo = objectMapper.readValue(textContent.text(), Map.class);
                    count++;
                    System.out.printf(
                            "%3d. Job Name: %-30s | Full Name: %s%n",
                            count, jobInfo.get("name"), jobInfo.get("fullName"));
                }
            }

            System.out.println("\n========================================");
            System.out.println("Total Jobs: " + count);
            System.out.println("========================================\n");
        }
    }
}
