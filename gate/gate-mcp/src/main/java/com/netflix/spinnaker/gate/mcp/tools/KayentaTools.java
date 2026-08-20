/*
 * Copyright 2026 McIntosh.farm
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

import com.netflix.spinnaker.gate.mcp.support.McpAccessGuard;
import com.netflix.spinnaker.gate.services.internal.KayentaService;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;

/**
 * MCP tools for Kayenta canary analysis: canary config CRUD, running canary judgements, and -
 * notably - {@code test_canary_metric_query}, which lets a caller validate a metric query against a
 * real metrics source (Prometheus, Datadog, Stackdriver, ...) before saving it into a canary
 * config, exactly like the "Test Query" button in Deck's canary config editor. Deck's UI talks to
 * Kayenta directly for that one feature (Gate never proxied it); {@link
 * KayentaService#queryMetrics} adds it to Gate's Kayenta client as a generic {@code
 * fetch/{provider}/query} passthrough, since every metrics provider exposes the same shape
 * (metricSetName/metricName plus provider-specific query params) at that path.
 *
 * <p>Only registered when Kayenta is enabled ({@code services.kayenta.enabled: true}) - see the
 * {@code @ConditionalOnProperty} guard in {@code McpServerAutoConfiguration}.
 */
public class KayentaTools {

  private final KayentaService kayentaService;
  private final McpAccessGuard accessGuard;

  public KayentaTools(KayentaService kayentaService, McpAccessGuard accessGuard) {
    this.kayentaService = kayentaService;
    this.accessGuard = accessGuard;
  }

  @McpTool(
      name = "list_canary_configs",
      description = "List canary configs, optionally filtered by application.")
  public List<Object> listCanaryConfigs(
      @McpToolParam(
              description = "Only include canary configs for this application",
              required = false)
          String application,
      @McpToolParam(description = "Kayenta configuration storage account name", required = false)
          String configurationAccountName) {
    return Retrofit2SyncCall.execute(
        kayentaService.getCanaryConfigs(application, configurationAccountName));
  }

  @McpTool(name = "get_canary_config", description = "Retrieve a single canary config by id.")
  public Map<String, Object> getCanaryConfig(
      @McpToolParam(description = "Canary config id", required = true) String id,
      @McpToolParam(description = "Kayenta configuration storage account name", required = false)
          String configurationAccountName) {
    return Retrofit2SyncCall.execute(kayentaService.getCanaryConfig(id, configurationAccountName));
  }

  @McpTool(
      name = "save_canary_config",
      description =
          "Create a new canary config, or update an existing one if 'id' is supplied. Validate metric queries with "
              + "test_canary_metric_query before saving.")
  public Map<String, Object> saveCanaryConfig(
      @McpToolParam(
              description = "Existing canary config id to update; omit to create a new config",
              required = false)
          String id,
      @McpToolParam(
              description = "Full canary config map (name, applications, metrics, judge, ...)",
              required = true)
          Map<String, Object> config,
      @McpToolParam(description = "Kayenta configuration storage account name", required = false)
          String configurationAccountName) {
    accessGuard.requireWriteAccess("save_canary_config");
    if (id != null) {
      return Retrofit2SyncCall.execute(
          kayentaService.updateCanaryConfig(id, config, configurationAccountName));
    }
    return Retrofit2SyncCall.execute(
        kayentaService.createCanaryConfig(config, configurationAccountName));
  }

  @McpTool(name = "delete_canary_config", description = "Delete a canary config by id.")
  public void deleteCanaryConfig(
      @McpToolParam(description = "Canary config id", required = true) String id,
      @McpToolParam(description = "Kayenta configuration storage account name", required = false)
          String configurationAccountName) {
    accessGuard.requireWriteAccess("delete_canary_config");
    Retrofit2SyncCall.execute(kayentaService.deleteCanaryConfig(id, configurationAccountName));
  }

  @McpTool(name = "list_canary_judges", description = "List all configured canary judges.")
  public List<Object> listCanaryJudges() {
    return Retrofit2SyncCall.execute(kayentaService.listJudges());
  }

  @McpTool(
      name = "list_canary_metrics_metadata",
      description =
          "List metric descriptors available from a metrics source, for populating a canary config's metric list.")
  public List<Object> listCanaryMetricsMetadata(
      @McpToolParam(
              description = "Filter string applied to the metric name/descriptor list",
              required = false)
          String filter,
      @McpToolParam(description = "Kayenta metrics account name", required = false)
          String metricsAccountName) {
    return Retrofit2SyncCall.execute(
        kayentaService.listMetricsServiceMetadata(filter, metricsAccountName));
  }

  @McpTool(
      name = "test_canary_metric_query",
      description =
          "Synchronously run a single metric query against a metrics source and return the results, without saving "
              + "anything - the same 'Test Query' feature in Deck's canary config editor. Use this to validate a "
              + "metricName/query template resolves to real data before adding it to a canary config.")
  public Map<String, Object> testCanaryMetricQuery(
      @McpToolParam(
              description =
                  "Metrics provider to query, e.g. 'prometheus', 'datadog', 'stackdriver', 'graphite', 'influxdb', "
                      + "'newrelic-insights', 'atlas', 'wavefront'",
              required = true)
          String provider,
      @McpToolParam(
              description = "Metric set name to label the results with, e.g. 'cpu'",
              required = true)
          String metricSetName,
      @McpToolParam(
              description =
                  "The metric name or query expression, e.g. 'node_cpu' (prometheus) or 'avg:system.cpu.user' (datadog)",
              required = true)
          String metricName,
      @McpToolParam(description = "Kayenta metrics account name", required = false)
          String metricsAccountName,
      @McpToolParam(description = "Kayenta storage account name", required = false)
          String storageAccountName,
      @McpToolParam(
              description =
                  "Additional provider-specific query parameters (e.g. 'scope', 'location', 'start', 'end', "
                      + "'groupByFields', 'project', 'resourceType') merged in as-is",
              required = false)
          Map<String, Object> extraQueryParameters) {
    Map<String, Object> queryParameters = new LinkedHashMap<>();
    if (metricsAccountName != null) {
      queryParameters.put("metricsAccountName", metricsAccountName);
    }
    if (storageAccountName != null) {
      queryParameters.put("storageAccountName", storageAccountName);
    }
    queryParameters.put("metricSetName", metricSetName);
    queryParameters.put("metricName", metricName);
    if (extraQueryParameters != null) {
      queryParameters.putAll(extraQueryParameters);
    }

    return Retrofit2SyncCall.execute(kayentaService.queryMetrics(provider, queryParameters));
  }

  @McpTool(
      name = "initiate_canary",
      description = "Start a canary judgement execution using a saved canary config.")
  public Map<String, Object> initiateCanary(
      @McpToolParam(description = "Canary config id to execute", required = true)
          String canaryConfigId,
      @McpToolParam(
              description = "Execution request (scopes, thresholds, judge name, ...)",
              required = true)
          Map<String, Object> executionRequest,
      @McpToolParam(
              description = "Application this canary run is associated with",
              required = false)
          String application,
      @McpToolParam(
              description = "Parent pipeline execution id, if run from a pipeline",
              required = false)
          String parentPipelineExecutionId,
      @McpToolParam(description = "Kayenta metrics account name", required = false)
          String metricsAccountName,
      @McpToolParam(description = "Kayenta storage account name", required = false)
          String storageAccountName,
      @McpToolParam(description = "Kayenta configuration storage account name", required = false)
          String configurationAccountName) {
    accessGuard.requireWriteAccess("initiate_canary");
    return Retrofit2SyncCall.execute(
        kayentaService.initiateCanary(
            canaryConfigId,
            executionRequest,
            application,
            parentPipelineExecutionId,
            metricsAccountName,
            storageAccountName,
            configurationAccountName));
  }

  @McpTool(
      name = "initiate_canary_with_config",
      description =
          "Start a canary judgement execution with an inline (unsaved) canary config - useful for testing a canary "
              + "config end-to-end before committing it with save_canary_config.")
  public Map<String, Object> initiateCanaryWithConfig(
      @McpToolParam(
              description =
                  "Ad-hoc execution request including the inline canary config and scopes",
              required = true)
          Map<String, Object> adhocExecutionRequest,
      @McpToolParam(
              description = "Application this canary run is associated with",
              required = false)
          String application,
      @McpToolParam(
              description = "Parent pipeline execution id, if run from a pipeline",
              required = false)
          String parentPipelineExecutionId,
      @McpToolParam(description = "Kayenta metrics account name", required = false)
          String metricsAccountName,
      @McpToolParam(description = "Kayenta storage account name", required = false)
          String storageAccountName) {
    accessGuard.requireWriteAccess("initiate_canary_with_config");
    return Retrofit2SyncCall.execute(
        kayentaService.initiateCanaryWithConfig(
            adhocExecutionRequest,
            application,
            parentPipelineExecutionId,
            metricsAccountName,
            storageAccountName));
  }

  @McpTool(
      name = "get_canary_result",
      description = "Retrieve a canary judgement execution's result.")
  public Map<String, Object> getCanaryResult(
      @McpToolParam(description = "Canary execution id", required = true) String canaryExecutionId,
      @McpToolParam(description = "Kayenta storage account name", required = false)
          String storageAccountName) {
    return Retrofit2SyncCall.execute(
        kayentaService.getCanaryResult(canaryExecutionId, storageAccountName));
  }

  @McpTool(
      name = "list_canary_results_by_application",
      description = "List canary judgement executions for an application, most recent first.")
  public List<Object> listCanaryResultsByApplication(
      @McpToolParam(description = "Application name", required = true) String application,
      @McpToolParam(description = "Maximum number of results to return", required = false)
          Integer limit,
      @McpToolParam(description = "Page number (1-indexed)", required = false) Integer page,
      @McpToolParam(
              description =
                  "Comma-delimited list of statuses to filter by, e.g. 'RUNNING,SUCCEEDED'",
              required = false)
          String statuses,
      @McpToolParam(description = "Kayenta storage account name", required = false)
          String storageAccountName) {
    return Retrofit2SyncCall.execute(
        kayentaService.getCanaryResultsByApplication(
            application,
            limit == null ? 20 : limit,
            page == null ? 1 : page,
            statuses,
            storageAccountName));
  }

  @McpTool(
      name = "get_canary_metric_set_pair_list",
      description =
          "Retrieve the metric set pair list for a canary result - the per-metric experiment/control data points "
              + "used to compute the judgement.")
  public List<Object> getCanaryMetricSetPairList(
      @McpToolParam(description = "Metric set pair list id, from a canary result", required = true)
          String metricSetPairListId,
      @McpToolParam(description = "Kayenta storage account name", required = false)
          String storageAccountName) {
    return Retrofit2SyncCall.execute(
        kayentaService.getMetricSetPairList(metricSetPairListId, storageAccountName));
  }
}
