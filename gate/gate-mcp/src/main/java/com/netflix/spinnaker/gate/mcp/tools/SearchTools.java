/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.mcp.tools;

import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;

/**
 * MCP tools for Spinnaker's global infrastructure/application search (clouddriver's {@code /search}
 * endpoint, backed by its Cats cache indices).
 *
 * <p><b>This proxies a genuinely quirky endpoint - read this before trusting its output:</b>
 *
 * <ul>
 *   <li>Gate's own {@code /search} REST endpoint requires exactly one {@code type} per call
 *       (`@RequestParam(value = "type") String type`, no default) even though clouddriver's
 *       underlying {@code SearchController} is documented as supporting an omitted/multi-value type
 *       ("if no value is supplied, all types will be returned") - clouddriver declares {@code type}
 *       as a {@code List<String>}, but Gate's Retrofit client ({@code ClouddriverService.search})
 *       declares it as a single {@code String}, so a multi-type request can never actually reach
 *       clouddriver as multiple values through Gate. Deck's own frontend works around exactly this
 *       by firing one request per registered search category and merging client-side (see {@code
 *       InfrastructureSearchServiceV2} in deck) - {@code search_all_types} below does the same
 *       thing, which is the only way to get an "everything" search through Gate today.
 *   <li>Short queries are silently short-circuited: with {@code allowShortQuery} unset/false, a
 *       query under 3 characters returns an empty list rather than erroring - both Gate's
 *       controller and this tool replicate that guard (clouddriver's own endpoint has no such
 *       guard, so calling clouddriver directly bypasses it, but Gate's UI-facing contract expects
 *       it).
 *   <li>When a query matches results from more than one clouddriver {@code SearchProvider} in a
 *       single call, clouddriver merges them into one result set and hardcodes its top-level {@code
 *       platform} field to {@code "aws"} regardless of which provider(s) actually matched (see the
 *       {@code TODO-cfieber} workaround for <a
 *       href="https://github.com/spinnaker/deck/issues/128">spinnaker/deck#128</a> in clouddriver's
 *       {@code SearchController} - unresolved as of this writing). Do not trust the aggregate
 *       {@code platform} field on a result set as "which cloud provider produced these results"
 *       when more than one provider could plausibly match; prefer per-result fields.
 * </ul>
 *
 * <p>Reimplemented directly against {@link ClouddriverServiceSelector} (a gate-core bean) since
 * gate-mcp cannot depend on gate-web's {@code SearchController}/{@code SearchService}.
 */
public class SearchTools {

  private static final Logger log = LoggerFactory.getLogger(SearchTools.class);

  /**
   * The categories Deck's own global search bar queries; see deck's {@code
   * searchResultType.registry}.
   */
  private static final List<String> DEFAULT_SEARCH_TYPES =
      List.of(
          "applications",
          "projects",
          "clusters",
          "serverGroups",
          "instances",
          "loadBalancers",
          "securityGroups");

  private static final int MIN_QUERY_LENGTH = 3;

  private final ClouddriverServiceSelector clouddriverServiceSelector;

  public SearchTools(ClouddriverServiceSelector clouddriverServiceSelector) {
    this.clouddriverServiceSelector = clouddriverServiceSelector;
  }

  @McpTool(
      name = "search_infrastructure",
      description =
          "Search Spinnaker infrastructure/applications for a single type, e.g. 'applications', 'projects', "
              + "'clusters', 'serverGroups', 'instances', 'loadBalancers', 'securityGroups' (some cloud providers "
              + "register additional types, e.g. 'certificates', 'subnets'). Backed by clouddriver's cached search "
              + "index, not a live infrastructure call. To search across all types at once, use search_all_types "
              + "instead - Gate's search API only accepts one type per call.")
  public List<Map> searchInfrastructure(
      @McpToolParam(
              description =
                  "Search phrase. Must be at least 3 characters unless allowShortQuery is true.",
              required = false)
          String query,
      @McpToolParam(
              description = "The result type to search, e.g. 'applications' or 'serverGroups'",
              required = true)
          String type,
      @McpToolParam(
              description = "Restrict results to this platform/provider, if the caller knows it",
              required = false)
          String platform,
      @McpToolParam(
              description =
                  "Maximum number of results to return. Defaults to 10000 (clouddriver's own default).",
              required = false)
          Integer pageSize,
      @McpToolParam(description = "Page number, 1-indexed. Defaults to 1.", required = false)
          Integer page,
      @McpToolParam(
              description =
                  "Allow queries shorter than 3 characters (normally short queries return no results)",
              required = false)
          Boolean allowShortQuery,
      @McpToolParam(
              description =
                  "Additional type-specific filters, e.g. {\"account\": \"prod\", \"region\": \"us-east-1\", "
                      + "\"stack\": \"canary\"} - supported filter keys vary by type",
              required = false)
          Map<String, String> filters) {
    if (isTooShort(query, allowShortQuery)) {
      return List.of();
    }
    return executeSearch(query, type, platform, pageSize, page, filters);
  }

  @McpTool(
      name = "search_all_types",
      description =
          "Search across all (or a chosen subset of) infrastructure/application types at once, merging the "
              + "per-type results - the MCP equivalent of Deck's global search bar. Gate's underlying /search API "
              + "only accepts one type per HTTP call, so this fires one search_infrastructure-equivalent call per "
              + "type and combines the results; a type that errors is reported separately rather than failing the "
              + "whole search.")
  public Map<String, Object> searchAllTypes(
      @McpToolParam(
              description =
                  "Search phrase. Must be at least 3 characters unless allowShortQuery is true.",
              required = false)
          String query,
      @McpToolParam(
              description =
                  "Types to search, e.g. ['applications', 'clusters']. Defaults to the standard set Deck's global "
                      + "search uses: applications, projects, clusters, serverGroups, instances, loadBalancers, "
                      + "securityGroups.",
              required = false)
          List<String> types,
      @McpToolParam(
              description = "Restrict results to this platform/provider, if the caller knows it",
              required = false)
          String platform,
      @McpToolParam(
              description =
                  "Maximum number of results to return per type. Defaults to 500 (Deck's own default).",
              required = false)
          Integer pageSize,
      @McpToolParam(
              description =
                  "Allow queries shorter than 3 characters (normally short queries return no results)",
              required = false)
          Boolean allowShortQuery,
      @McpToolParam(
              description = "Additional type-specific filters applied to every type searched",
              required = false)
          Map<String, String> filters) {
    List<String> searchTypes = (types == null || types.isEmpty()) ? DEFAULT_SEARCH_TYPES : types;
    int effectivePageSize = pageSize == null ? 500 : pageSize;

    Map<String, Object> resultsByType = new LinkedHashMap<>();
    Map<String, String> errorsByType = new LinkedHashMap<>();

    if (isTooShort(query, allowShortQuery)) {
      for (String type : searchTypes) {
        resultsByType.put(type, List.of());
      }
      return summarize(query, resultsByType, errorsByType);
    }

    for (String type : searchTypes) {
      try {
        resultsByType.put(
            type, executeSearch(query, type, platform, effectivePageSize, 1, filters));
      } catch (Exception e) {
        log.warn("search_all_types: search failed for type '{}'", type, e);
        errorsByType.put(type, String.valueOf(e.getMessage()));
        resultsByType.put(type, List.of());
      }
    }

    return summarize(query, resultsByType, errorsByType);
  }

  private List<Map> executeSearch(
      String query,
      String type,
      String platform,
      Integer pageSize,
      Integer page,
      Map<String, String> filters) {
    return Retrofit2SyncCall.execute(
        clouddriverServiceSelector
            .select()
            .search(
                query == null ? "" : query,
                type,
                platform,
                pageSize,
                page == null ? 1 : page,
                filters == null ? Map.of() : filters));
  }

  private static boolean isTooShort(String query, Boolean allowShortQuery) {
    return !Boolean.TRUE.equals(allowShortQuery)
        && (query == null || query.length() < MIN_QUERY_LENGTH);
  }

  private static Map<String, Object> summarize(
      String query, Map<String, Object> resultsByType, Map<String, String> errorsByType) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("query", query);
    summary.put("resultsByType", resultsByType);
    if (!errorsByType.isEmpty()) {
      summary.put("errorsByType", errorsByType);
    }
    return summary;
  }
}
