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
 * <p><b>Read this before trusting the output - what was fixed and what's still quirky:</b>
 *
 * <ul>
 *   <li><b>Fixed here:</b> Gate's own {@code /search} REST endpoint and its Retrofit client
 *       (`ClouddriverService.search` in gate-core) used to declare {@code type} as a single {@code
 *       String}, even though clouddriver's backend {@code SearchController}/{@code SearchProvider}
 *       always accepted a {@code List<String>} and natively searches multiple types in one pass
 *       over its cache (see {@code CatsSearchProvider.findMatches}, which does one combined
 *       cache-identifier scan across all requested types rather than one scan per type). That meant
 *       a multi-type request could never reach clouddriver as multiple values through Gate - fixed
 *       by changing {@code type} to {@code List<String>} end-to-end (gate-core's {@code
 *       ClouddriverService}, gate-web's {@code SearchService}/{@code SearchController}). {@code
 *       search_infrastructure} below now accepts multiple types in a single call, which is
 *       genuinely cheaper than firing one request per type: one HTTP round trip and one
 *       permission-check pass instead of N.
 *   <li><b>Still true, and important:</b> when searching multiple types in one call, {@code
 *       pageSize}/{@code page} apply to the *combined, relevance-sorted* match list across all
 *       requested types, not independently per type - a type with many matches can crowd out a type
 *       with few in the same page. If you want a fair/independent result budget per category (e.g.
 *       "show me some of everything"), use {@code search_all_types} instead, which fires one call
 *       per type (mirroring Deck's own global search bar, see {@code InfrastructureSearchServiceV2}
 *       in deck) so each type gets its own page/pageSize.
 *   <li><b>Still broken upstream, not fixed here:</b> clouddriver's own {@code SearchController}
 *       declares {@code type} as {@code required} with no default, despite its javadoc claiming an
 *       omitted type searches everything - so a truly typeless "search absolutely everything in one
 *       call" was never possible and still isn't; you must supply at least one type. Also, when a
 *       query matches results from more than one clouddriver {@code SearchProvider}, clouddriver
 *       merges them into one result set and hardcodes its top-level {@code platform} field to
 *       {@code "aws"} regardless of which provider(s) actually matched (see the {@code
 *       CatsSearchProvider.getPlatform()} `// TODO(cfieber) - need a better story around this`, and
 *       the {@code SearchController} workaround for <a
 *       href="https://github.com/spinnaker/deck/issues/128">spinnaker/deck#128</a> - both
 *       unresolved upstream as of this writing). Don't trust the aggregate {@code platform} field
 *       as "which provider produced these results" when more than one could match; prefer
 *       per-result fields.
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
          "Search Spinnaker infrastructure/applications, e.g. types 'applications', 'projects', 'clusters', "
              + "'serverGroups', 'instances', 'loadBalancers', 'securityGroups' (some cloud providers register "
              + "additional types, e.g. 'certificates', 'subnets'). Backed by clouddriver's cached search index, "
              + "not a live infrastructure call. Searching multiple types in one call is efficient (one request, "
              + "one combined cache scan) but shares a single pageSize/page budget across all of them, sorted by "
              + "relevance - if you need a fair number of results from *each* type instead, use search_all_types.")
  public List<Map> searchInfrastructure(
      @McpToolParam(
              description =
                  "Search phrase. Must be at least 3 characters unless allowShortQuery is true.",
              required = false)
          String query,
      @McpToolParam(
              description =
                  "One or more result types to search, e.g. ['applications'] or ['serverGroups', 'instances']",
              required = true)
          List<String> types,
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
    return executeSearch(query, types, platform, pageSize, page, filters);
  }

  @McpTool(
      name = "search_all_types",
      description =
          "Search across all (or a chosen subset of) infrastructure/application types at once, giving each type "
              + "its own independent result budget and merging the per-type results - the MCP equivalent of Deck's "
              + "global search bar. Use this over search_infrastructure when you want a fair sampling across "
              + "categories rather than one relevance-ranked list; a type that errors is reported separately "
              + "rather than failing the whole search.")
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
            type, executeSearch(query, List.of(type), platform, effectivePageSize, 1, filters));
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
      List<String> types,
      String platform,
      Integer pageSize,
      Integer page,
      Map<String, String> filters) {
    return Retrofit2SyncCall.execute(
        clouddriverServiceSelector
            .select()
            .search(
                query == null ? "" : query,
                types,
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
