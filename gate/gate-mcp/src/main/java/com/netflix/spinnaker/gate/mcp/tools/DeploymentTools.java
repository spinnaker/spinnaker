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

import com.netflix.spinnaker.gate.mcp.support.OrchestrationJobs;
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;

/**
 * MCP tools for reading and mutating infrastructure: clusters, server groups and load balancers.
 *
 * <p>{@code submit_orchestration} is the generic escape hatch that every Deck write operation
 * ultimately uses (see {@code TaskService.createAndWaitForCompletion} in gate-core) - it accepts a
 * raw Orca job list, so any clouddriver-supported operation (any cloud provider, any operation
 * type) can be submitted even if this module doesn't yet have a typed convenience tool for it.
 * {@code deploy_aws_server_group} and {@code upsert_load_balancer} are typed/semi-typed convenience
 * wrappers over that same primitive for the most common AWS cases.
 */
public class DeploymentTools {

  private final ClouddriverServiceSelector clouddriverServiceSelector;
  private final OrchestrationJobs orchestrationJobs;

  public DeploymentTools(
      ClouddriverServiceSelector clouddriverServiceSelector, OrchestrationJobs orchestrationJobs) {
    this.clouddriverServiceSelector = clouddriverServiceSelector;
    this.orchestrationJobs = orchestrationJobs;
  }

  @McpTool(
      name = "get_clusters",
      description = "List an application's clusters, grouped by account.")
  @SuppressWarnings("unchecked")
  public Map<String, Object> getClusters(
      @McpToolParam(description = "Application name", required = true) String application) {
    return Retrofit2SyncCall.execute(clouddriverServiceSelector.select().getClusters(application));
  }

  @McpTool(
      name = "get_server_groups",
      description =
          "List an application's server groups, optionally filtered by cloud provider or cluster name.")
  public List<Object> getServerGroups(
      @McpToolParam(description = "Application name", required = true) String application,
      @McpToolParam(
              description = "Cloud provider to filter by, e.g. 'aws' or 'kubernetes'",
              required = false)
          String cloudProvider,
      @McpToolParam(
              description = "Comma-separated list of cluster names to filter by",
              required = false)
          String clusters) {
    return Retrofit2SyncCall.execute(
        clouddriverServiceSelector
            .select()
            .getServerGroups(application, "true", cloudProvider, clusters));
  }

  @McpTool(
      name = "get_load_balancers",
      description = "List all load balancers known to clouddriver for a given cloud provider.")
  public List<Map> getLoadBalancers(
      @McpToolParam(
              description = "Cloud provider, e.g. 'aws' or 'kubernetes'. Defaults to 'aws'.",
              required = false)
          String cloudProvider) {
    return Retrofit2SyncCall.execute(
        clouddriverServiceSelector
            .select()
            .getLoadBalancers(cloudProvider == null ? "aws" : cloudProvider));
  }

  @McpTool(
      name = "submit_orchestration",
      description =
          "Submit a raw Orca orchestration: a list of clouddriver/orca operation job maps, exactly as Deck would build them "
              + "(each job needs at least a 'type' key, e.g. 'createServerGroup', 'upsertLoadBalancer', 'disableServerGroup', "
              + "'resizeServerGroup', 'upsertSecurityGroup', plus 'cloudProvider' and whatever fields that operation's clouddriver "
              + "AtomicOperationConverter expects). Use this for any capability that doesn't have a dedicated typed tool. Blocks "
              + "until the orchestration completes or fails.")
  public Map<String, Object> submitOrchestration(
      @McpToolParam(description = "Application the orchestration runs against", required = true)
          String application,
      @McpToolParam(
              description = "Human-readable description shown in the Spinnaker UI",
              required = true)
          String description,
      @McpToolParam(
              description =
                  "List of job maps to submit, e.g. [{\"type\": \"createServerGroup\", ...}]",
              required = true)
          List<Map<String, Object>> jobs) {
    orchestrationJobs.requireWriteAccess("submit_orchestration");
    return orchestrationJobs.submit("submit_orchestration", application, description, jobs);
  }

  @McpTool(
      name = "deploy_aws_server_group",
      description =
          "Deploy a new AWS server group (Auto Scaling Group) into a cluster from a standard AMI id. "
              + "Equivalent to Deck's 'Deploy' stage/button for the AWS provider.")
  public Map<String, Object> deployAwsServerGroup(
      @McpToolParam(description = "Application name", required = true) String application,
      @McpToolParam(description = "AWS account name", required = true) String account,
      @McpToolParam(description = "AWS region, e.g. 'us-east-1'", required = true) String region,
      @McpToolParam(description = "AMI id to deploy, e.g. 'ami-0123456789abcdef0'", required = true)
          String amiName,
      @McpToolParam(description = "Cluster 'stack' qualifier, e.g. 'prod'", required = false)
          String stack,
      @McpToolParam(
              description = "Cluster free-form detail qualifier, e.g. 'canary'",
              required = false)
          String freeFormDetails,
      @McpToolParam(description = "EC2 instance type, e.g. 'm5.large'", required = true)
          String instanceType,
      @McpToolParam(
              description = "Subnet type/purpose to deploy into, e.g. 'internal'",
              required = false)
          String subnetType,
      @McpToolParam(description = "Minimum server group capacity", required = true) Integer min,
      @McpToolParam(description = "Desired server group capacity", required = true) Integer desired,
      @McpToolParam(description = "Maximum server group capacity", required = true) Integer max,
      @McpToolParam(
              description = "Security group names/ids to attach to the server group",
              required = false)
          List<String> securityGroups,
      @McpToolParam(
              description = "Load balancer names to attach the server group to",
              required = false)
          List<String> loadBalancers) {
    orchestrationJobs.requireWriteAccess("deploy_aws_server_group");

    Map<String, Object> capacity = new LinkedHashMap<>();
    capacity.put("min", min);
    capacity.put("desired", desired);
    capacity.put("max", max);

    Map<String, Object> job = new LinkedHashMap<>();
    job.put("type", "createServerGroup");
    job.put("cloudProvider", "aws");
    job.put("application", application);
    job.put("account", account);
    job.put("credentials", account);
    job.put("region", region);
    job.put("amiName", amiName);
    job.put("instanceType", instanceType);
    job.put("capacity", capacity);
    job.put("availabilityZones", Map.of(region, List.of(region + "a", region + "b", region + "c")));
    if (stack != null) {
      job.put("stack", stack);
    }
    if (freeFormDetails != null) {
      job.put("freeFormDetails", freeFormDetails);
    }
    if (subnetType != null) {
      job.put("subnetType", subnetType);
    }
    if (securityGroups != null) {
      job.put("securityGroups", securityGroups);
    }
    if (loadBalancers != null) {
      job.put("loadBalancers", loadBalancers);
    }

    String clusterName =
        String.join("-", nonNull(application), nonNull(stack), nonNull(freeFormDetails))
            .replaceAll("-+$", "");
    return orchestrationJobs.submit(
        "deploy_aws_server_group",
        application,
        "Deploy server group in cluster " + clusterName,
        List.of(job));
  }

  @McpTool(
      name = "upsert_load_balancer",
      description =
          "Create or update a load balancer. Because load balancer schemas vary significantly by cloud provider and load "
              + "balancer type (classic ELB vs ALB/NLB vs GCP vs Kubernetes service, etc.), provider-specific fields (listeners, "
              + "health checks, target groups, subnets, ...) are passed through 'extraFields' exactly as clouddriver's "
              + "UpsertLoadBalancer operation for that provider expects them.")
  public Map<String, Object> upsertLoadBalancer(
      @McpToolParam(description = "Application name", required = true) String application,
      @McpToolParam(description = "Account name", required = true) String account,
      @McpToolParam(description = "Cloud provider, e.g. 'aws' or 'kubernetes'", required = true)
          String cloudProvider,
      @McpToolParam(description = "Region (or namespace, for Kubernetes)", required = true)
          String region,
      @McpToolParam(description = "Load balancer name", required = true) String name,
      @McpToolParam(
              description =
                  "Provider-specific fields (listeners, healthCheck, subnetType, vpcId, targetGroups, ...) merged directly into the job",
              required = false)
          Map<String, Object> extraFields) {
    orchestrationJobs.requireWriteAccess("upsert_load_balancer");

    Map<String, Object> job = new LinkedHashMap<>();
    job.put("type", "upsertLoadBalancer");
    job.put("cloudProvider", cloudProvider);
    job.put("application", application);
    job.put("account", account);
    job.put("credentials", account);
    job.put("region", region);
    job.put("name", name);
    if (extraFields != null) {
      job.putAll(extraFields);
    }

    return orchestrationJobs.submit(
        "upsert_load_balancer", application, "Upsert load balancer '" + name + "'", List.of(job));
  }

  private static String nonNull(String value) {
    return value == null ? "" : value;
  }
}
