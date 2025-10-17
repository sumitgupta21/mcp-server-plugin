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
import hudson.PluginWrapper;
import io.jenkins.plugins.mcp.server.McpServerExtension;
import io.jenkins.plugins.mcp.server.annotation.Tool;
import io.jenkins.plugins.mcp.server.annotation.ToolParam;
import jakarta.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import jenkins.model.Jenkins;
import lombok.extern.slf4j.Slf4j;

/**
 * MCP Server Extension that provides tools for retrieving information about installed Jenkins plugins.
 */
@Extension
@Slf4j
public class PluginsExtension implements McpServerExtension {

    /**
     * Simple plugin info record for serialization.
     */
    public record PluginInfo(
            String shortName,
            String displayName,
            String version,
            boolean active,
            boolean enabled,
            boolean hasUpdate,
            String url) {}

    @Tool(
            description =
                    "Get a paginated list of all installed Jenkins plugins, sorted by name. Returns information about each plugin including name, version, and status.",
            annotations = @Tool.Annotations(destructiveHint = false))
    public List<PluginInfo> getInstalledPlugins(
            @Nullable
                    @ToolParam(
                            description =
                                    "The 0 based starting index, if not specified, then start from the first (0)",
                            required = false)
                    Integer skip,
            @Nullable
                    @ToolParam(
                            description =
                                    "The maximum number of items to return. If not specified, returns 50 items. Cannot exceed 100 items.",
                            required = false)
                    Integer limit,
            @Nullable
                    @ToolParam(
                            description = "Filter to show only active plugins (true/false)",
                            required = false)
                    Boolean activeOnly) {

        if (skip == null || skip < 0) {
            skip = 0;
        }
        if (limit == null || limit < 0 || limit > 100) {
            limit = 50;
        }

        var plugins = Jenkins.get().getPluginManager().getPlugins();
        var pluginStream = plugins.stream();

        // Apply filter for active plugins only if requested
        if (activeOnly != null && activeOnly) {
            pluginStream = pluginStream.filter(PluginWrapper::isActive);
        }

        return pluginStream
                .sorted(Comparator.comparing(PluginWrapper::getShortName))
                .skip(skip)
                .limit(limit)
                .map(plugin -> new PluginInfo(
                        plugin.getShortName(),
                        plugin.getDisplayName(),
                        plugin.getVersion(),
                        plugin.isActive(),
                        plugin.isEnabled(),
                        plugin.hasUpdate(),
                        plugin.getUrl()))
                .toList();
    }

    @Tool(
            description = "Get detailed information about a specific Jenkins plugin by its short name",
            annotations = @Tool.Annotations(destructiveHint = false))
    public PluginInfo getPlugin(
            @ToolParam(description = "The short name of the plugin (e.g., 'git', 'workflow-aggregator')")
                    String pluginShortName) {
        var plugin = Jenkins.get().getPluginManager().getPlugin(pluginShortName);
        if (plugin == null) {
            return null;
        }

        return new PluginInfo(
                plugin.getShortName(),
                plugin.getDisplayName(),
                plugin.getVersion(),
                plugin.isActive(),
                plugin.isEnabled(),
                plugin.hasUpdate(),
                plugin.getUrl());
    }
}
