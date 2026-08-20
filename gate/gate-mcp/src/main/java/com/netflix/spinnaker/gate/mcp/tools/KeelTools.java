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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.mcp.support.McpAccessGuard;
import com.netflix.spinnaker.gate.model.manageddelivery.ConstraintState;
import com.netflix.spinnaker.gate.model.manageddelivery.ConstraintStatus;
import com.netflix.spinnaker.gate.model.manageddelivery.DeliveryConfig;
import com.netflix.spinnaker.gate.model.manageddelivery.EnvironmentArtifactPin;
import com.netflix.spinnaker.gate.model.manageddelivery.EnvironmentArtifactVeto;
import com.netflix.spinnaker.gate.model.manageddelivery.OverrideVerificationRequest;
import com.netflix.spinnaker.gate.model.manageddelivery.Resource;
import com.netflix.spinnaker.gate.model.manageddelivery.RetryVerificationRequest;
import com.netflix.spinnaker.gate.services.internal.KeelService;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;

/**
 * MCP tools for Managed Delivery (Keel): declarative delivery config manifests, environment/
 * constraint/verification status, and the controls an operator uses when a rollout is stuck -
 * pausing management, pinning/vetoing artifact versions, marking a version bad/good, and overriding
 * or retrying a verification. This is the highest-value CD surface for an MCP agent short of
 * pipeline executions themselves: "why is prod stuck", "veto this bad build", "what's pinned right
 * now" map directly onto these tools.
 *
 * <p>Mirrors {@code ManagedController} in gate-web (itself a thin proxy over {@link KeelService}, a
 * gate-core bean), reimplemented directly against {@link KeelService} since gate-mcp cannot depend
 * on gate-web. Small request bodies ({@link EnvironmentArtifactPin}, {@link
 * EnvironmentArtifactVeto}, {@link ConstraintStatus}, verification requests) are exposed as
 * individual scalar parameters rather than opaque maps, for a cleaner tool-call schema; the full
 * delivery config manifest and ad-hoc resource bodies are passed as free-form maps and converted to
 * their model types with Jackson, since a manifest's shape is too large/nested to usefully flatten.
 *
 * <p>Only registered when Managed Delivery is enabled ({@code services.keel.enabled: true}).
 */
public class KeelTools {

  private final KeelService keelService;
  private final ObjectMapper objectMapper;
  private final McpAccessGuard accessGuard;

  public KeelTools(KeelService keelService, ObjectMapper objectMapper, McpAccessGuard accessGuard) {
    this.keelService = keelService;
    this.objectMapper = objectMapper;
    this.accessGuard = accessGuard;
  }

  @McpTool(
      name = "get_delivery_config",
      description = "Retrieve a delivery config manifest by name.")
  public DeliveryConfig getDeliveryConfig(
      @McpToolParam(description = "Delivery config manifest name", required = true) String name) {
    return Retrofit2SyncCall.execute(keelService.getManifest(name));
  }

  @McpTool(
      name = "get_delivery_config_for_application",
      description = "Retrieve the delivery config manifest associated with an application.")
  public DeliveryConfig getDeliveryConfigForApplication(
      @McpToolParam(description = "Application name", required = true) String application) {
    return Retrofit2SyncCall.execute(keelService.getConfigBy(application));
  }

  @McpTool(
      name = "get_delivery_config_artifacts",
      description =
          "Get the status of each version of each artifact tracked by a delivery config, across all environments.")
  public List<Map<String, Object>> getDeliveryConfigArtifacts(
      @McpToolParam(description = "Delivery config manifest name", required = true) String name) {
    return Retrofit2SyncCall.execute(keelService.getManifestArtifacts(name));
  }

  @McpTool(
      name = "get_delivery_config_schema",
      description =
          "Retrieve the JSON schema for a delivery config manifest - use this to construct a valid manifest.")
  public Map<String, Object> getDeliveryConfigSchema() {
    return Retrofit2SyncCall.execute(keelService.schema());
  }

  @McpTool(
      name = "validate_delivery_config",
      description =
          "Validate a delivery config manifest without saving it - use this to check a manifest before calling "
              + "save_delivery_config.")
  public Map<String, Object> validateDeliveryConfig(
      @McpToolParam(description = "Full delivery config manifest map", required = true)
          Map<String, Object> manifest) {
    return Retrofit2SyncCall.execute(keelService.validateManifest(toDeliveryConfig(manifest)));
  }

  @McpTool(
      name = "diff_delivery_config",
      description =
          "Ad-hoc validate and diff a delivery config manifest against its current persisted state, without saving "
              + "it - shows what would change if it were saved.")
  public List<Map> diffDeliveryConfig(
      @McpToolParam(description = "Full delivery config manifest map", required = true)
          Map<String, Object> manifest) {
    return Retrofit2SyncCall.execute(keelService.diffManifest(toDeliveryConfig(manifest)));
  }

  @McpTool(
      name = "save_delivery_config",
      description =
          "Create or update a delivery config manifest. Validate it first with validate_delivery_config.")
  public DeliveryConfig saveDeliveryConfig(
      @McpToolParam(description = "Full delivery config manifest map", required = true)
          Map<String, Object> manifest) {
    accessGuard.requireWriteAccess("save_delivery_config");
    return Retrofit2SyncCall.execute(keelService.upsertManifest(toDeliveryConfig(manifest)));
  }

  @McpTool(
      name = "delete_delivery_config",
      description = "Delete a delivery config manifest by name.")
  public DeliveryConfig deleteDeliveryConfig(
      @McpToolParam(description = "Delivery config manifest name", required = true) String name) {
    accessGuard.requireWriteAccess("delete_delivery_config");
    return Retrofit2SyncCall.execute(keelService.deleteManifest(name));
  }

  @McpTool(
      name = "get_managed_application",
      description =
          "Get Managed Delivery details for an application: environments, resources, and their status.")
  public Map getManagedApplication(
      @McpToolParam(description = "Application name", required = true) String application,
      @McpToolParam(
              description = "Include full resource/environment detail, not just a summary",
              required = false)
          Boolean includeDetails,
      @McpToolParam(
              description =
                  "Entity types to include, e.g. 'resources', 'environments'. Defaults to 'resources'.",
              required = false)
          List<String> entities,
      @McpToolParam(
              description = "Maximum number of artifact versions to include per artifact",
              required = false)
          Integer maxArtifactVersions) {
    return Retrofit2SyncCall.execute(
        keelService.getApplicationDetails(
            application,
            includeDetails != null && includeDetails,
            entities == null ? List.of("resources") : entities,
            maxArtifactVersions));
  }

  @McpTool(
      name = "get_managed_environments",
      description =
          "Get the current environments (and their artifact/constraint state) for an application.")
  public List<Map<String, Object>> getManagedEnvironments(
      @McpToolParam(description = "Application name", required = true) String application) {
    return Retrofit2SyncCall.execute(keelService.getEnvironments(application));
  }

  @McpTool(
      name = "get_environment_constraints",
      description =
          "List the most recent constraint states (e.g. manual judgment, allowed-times, canary) for an environment "
              + "- use this to see why an artifact version is or isn't allowed to promote.")
  public List<ConstraintState> getEnvironmentConstraints(
      @McpToolParam(description = "Application name", required = true) String application,
      @McpToolParam(description = "Environment name", required = true) String environment,
      @McpToolParam(description = "Maximum number of constraint states to return", required = false)
          Integer limit) {
    return Retrofit2SyncCall.execute(
        keelService.getConstraintState(application, environment, limit == null ? 10 : limit));
  }

  @McpTool(
      name = "update_environment_constraint_status",
      description =
          "Approve or reject a pending constraint (e.g. a manual judgment) blocking an artifact version's promotion.")
  public void updateEnvironmentConstraintStatus(
      @McpToolParam(description = "Application name", required = true) String application,
      @McpToolParam(description = "Environment name", required = true) String environment,
      @McpToolParam(description = "Constraint type, e.g. 'manual-judgement'", required = true)
          String type,
      @McpToolParam(description = "Artifact reference the constraint applies to", required = true)
          String artifactReference,
      @McpToolParam(description = "Artifact version the constraint applies to", required = true)
          String artifactVersion,
      @McpToolParam(
              description =
                  "New status: 'PENDING', 'APPROVED', 'FAIL', or 'OVERRIDE_PASS'/'OVERRIDE_FAIL'",
              required = true)
          String status,
      @McpToolParam(description = "Optional comment explaining the decision", required = false)
          String comment) {
    accessGuard.requireWriteAccess("update_environment_constraint_status");

    ConstraintStatus constraintStatus = new ConstraintStatus();
    constraintStatus.setType(type);
    constraintStatus.setArtifactReference(artifactReference);
    constraintStatus.setArtifactVersion(artifactVersion);
    constraintStatus.setStatus(status);
    constraintStatus.setComment(comment);

    Retrofit2SyncCall.executeCall(
        keelService.updateConstraintStatus(application, environment, constraintStatus));
  }

  @McpTool(
      name = "get_managed_resource",
      description = "Retrieve a single Managed Delivery resource by id.")
  public Resource getManagedResource(
      @McpToolParam(description = "Resource id", required = true) String resourceId) {
    return Retrofit2SyncCall.execute(keelService.getResource(resourceId));
  }

  @McpTool(
      name = "get_managed_resource_status",
      description = "Get the current status of a Managed Delivery resource.")
  public Map<String, String> getManagedResourceStatus(
      @McpToolParam(description = "Resource id", required = true) String resourceId) {
    return Map.of("status", Retrofit2SyncCall.execute(keelService.getResourceStatus(resourceId)));
  }

  @McpTool(
      name = "pause_managed_application",
      description =
          "Pause Managed Delivery for an entire application - stops all automated management of its resources.")
  public void pauseManagedApplication(
      @McpToolParam(description = "Application name", required = true) String application) {
    accessGuard.requireWriteAccess("pause_managed_application");
    Retrofit2SyncCall.executeCall(
        keelService.pauseApplication(application, Collections.emptyMap()));
  }

  @McpTool(
      name = "resume_managed_application",
      description = "Resume Managed Delivery for a paused application.")
  public void resumeManagedApplication(
      @McpToolParam(description = "Application name", required = true) String application) {
    accessGuard.requireWriteAccess("resume_managed_application");
    Retrofit2SyncCall.executeCall(keelService.resumeApplication(application));
  }

  @McpTool(
      name = "pause_managed_resource",
      description = "Pause Managed Delivery for a single resource.")
  public void pauseManagedResource(
      @McpToolParam(description = "Resource id", required = true) String resourceId) {
    accessGuard.requireWriteAccess("pause_managed_resource");
    Retrofit2SyncCall.executeCall(keelService.pauseResource(resourceId, Collections.emptyMap()));
  }

  @McpTool(
      name = "resume_managed_resource",
      description = "Resume Managed Delivery for a paused resource.")
  public void resumeManagedResource(
      @McpToolParam(description = "Resource id", required = true) String resourceId) {
    accessGuard.requireWriteAccess("resume_managed_resource");
    Retrofit2SyncCall.executeCall(keelService.resumeResource(resourceId));
  }

  @McpTool(
      name = "pin_artifact_version",
      description =
          "Pin a specific artifact version in an environment, preventing newer versions from being promoted there "
              + "until unpinned.")
  public void pinArtifactVersion(
      @McpToolParam(description = "Application name", required = true) String application,
      @McpToolParam(description = "Environment to pin the version in", required = true)
          String targetEnvironment,
      @McpToolParam(description = "Artifact reference", required = true) String reference,
      @McpToolParam(description = "Artifact version to pin", required = true) String version,
      @McpToolParam(description = "Who/what is creating this pin", required = false)
          String pinnedBy,
      @McpToolParam(description = "Optional comment explaining why", required = false)
          String comment) {
    accessGuard.requireWriteAccess("pin_artifact_version");

    EnvironmentArtifactPin pin = new EnvironmentArtifactPin();
    pin.setTargetEnvironment(targetEnvironment);
    pin.setReference(reference);
    pin.setVersion(version);
    pin.setPinnedBy(pinnedBy);
    pin.setComment(comment);

    Retrofit2SyncCall.executeCall(keelService.pin(application, pin));
  }

  @McpTool(
      name = "unpin_artifact_version",
      description =
          "Remove an artifact pin from an environment. If 'reference' is omitted, all pinned artifacts in the "
              + "environment are unpinned.")
  public void unpinArtifactVersion(
      @McpToolParam(description = "Application name", required = true) String application,
      @McpToolParam(description = "Environment to unpin", required = true) String targetEnvironment,
      @McpToolParam(
              description =
                  "Artifact reference to unpin; omit to unpin everything in the environment",
              required = false)
          String reference) {
    accessGuard.requireWriteAccess("unpin_artifact_version");
    Retrofit2SyncCall.executeCall(
        keelService.deletePinForEnvironment(application, targetEnvironment, reference));
  }

  @McpTool(
      name = "veto_artifact_version",
      description =
          "Veto an artifact version in an environment, blocking it from being deployed there.")
  public void vetoArtifactVersion(
      @McpToolParam(description = "Application name", required = true) String application,
      @McpToolParam(description = "Environment to veto the version in", required = true)
          String targetEnvironment,
      @McpToolParam(description = "Artifact reference", required = true) String reference,
      @McpToolParam(description = "Artifact version to veto", required = true) String version,
      @McpToolParam(description = "Optional comment explaining why", required = false)
          String comment) {
    accessGuard.requireWriteAccess("veto_artifact_version");
    Retrofit2SyncCall.executeCall(
        keelService.veto(application, buildVeto(targetEnvironment, reference, version, comment)));
  }

  @McpTool(
      name = "remove_artifact_veto",
      description = "Remove a veto on an artifact version in an environment.")
  public void removeArtifactVeto(
      @McpToolParam(description = "Application name", required = true) String application,
      @McpToolParam(description = "Environment the veto applies to", required = true)
          String targetEnvironment,
      @McpToolParam(description = "Artifact reference", required = true) String reference,
      @McpToolParam(description = "Artifact version", required = true) String version) {
    accessGuard.requireWriteAccess("remove_artifact_veto");
    Retrofit2SyncCall.executeCall(
        keelService.deleteVeto(application, targetEnvironment, reference, version));
  }

  @McpTool(
      name = "mark_artifact_version_bad",
      description =
          "Permanently mark an artifact version as bad across all environments (stronger than a veto - it will "
              + "never be promoted again).")
  public void markArtifactVersionBad(
      @McpToolParam(description = "Application name", required = true) String application,
      @McpToolParam(description = "Environment the artifact is in", required = true)
          String targetEnvironment,
      @McpToolParam(description = "Artifact reference", required = true) String reference,
      @McpToolParam(description = "Artifact version to mark bad", required = true) String version,
      @McpToolParam(description = "Optional comment explaining why", required = false)
          String comment) {
    accessGuard.requireWriteAccess("mark_artifact_version_bad");
    Retrofit2SyncCall.executeCall(
        keelService.markBad(
            application, buildVeto(targetEnvironment, reference, version, comment)));
  }

  @McpTool(
      name = "mark_artifact_version_good",
      description = "Clear a 'bad' mark on an artifact version, allowing it to be promoted again.")
  public void markArtifactVersionGood(
      @McpToolParam(description = "Application name", required = true) String application,
      @McpToolParam(description = "Environment the artifact is in", required = true)
          String targetEnvironment,
      @McpToolParam(description = "Artifact reference", required = true) String reference,
      @McpToolParam(description = "Artifact version to mark good", required = true) String version,
      @McpToolParam(description = "Optional comment explaining why", required = false)
          String comment) {
    accessGuard.requireWriteAccess("mark_artifact_version_good");
    Retrofit2SyncCall.executeCall(
        keelService.markGood(
            application, buildVeto(targetEnvironment, reference, version, comment)));
  }

  @McpTool(
      name = "override_verification",
      description =
          "Manually override the status of a post-deploy verification (e.g. force it to pass or fail).")
  public void overrideVerification(
      @McpToolParam(description = "Application name", required = true) String application,
      @McpToolParam(description = "Environment name", required = true) String environment,
      @McpToolParam(description = "Verification id to override", required = true)
          String verificationId,
      @McpToolParam(description = "Artifact reference", required = true) String artifactReference,
      @McpToolParam(description = "Artifact version", required = true) String artifactVersion,
      @McpToolParam(description = "New status, e.g. 'PASS' or 'FAIL'", required = true)
          String status,
      @McpToolParam(description = "Optional comment explaining why", required = false)
          String comment) {
    accessGuard.requireWriteAccess("override_verification");

    OverrideVerificationRequest request =
        new OverrideVerificationRequest(verificationId, artifactReference, artifactVersion, status);
    request.setComment(comment);

    Retrofit2SyncCall.executeCall(
        keelService.overrideVerification(application, environment, request));
  }

  @McpTool(
      name = "retry_verification",
      description = "Retry a post-deploy verification for an artifact version.")
  public void retryVerification(
      @McpToolParam(description = "Application name", required = true) String application,
      @McpToolParam(description = "Environment name", required = true) String environment,
      @McpToolParam(description = "Verification id to retry", required = true)
          String verificationId,
      @McpToolParam(description = "Artifact reference", required = true) String artifactReference,
      @McpToolParam(description = "Artifact version", required = true) String artifactVersion) {
    accessGuard.requireWriteAccess("retry_verification");

    RetryVerificationRequest request =
        new RetryVerificationRequest(verificationId, artifactReference, artifactVersion);

    Retrofit2SyncCall.executeCall(keelService.retryVerification(application, environment, request));
  }

  private static EnvironmentArtifactVeto buildVeto(
      String targetEnvironment, String reference, String version, String comment) {
    EnvironmentArtifactVeto veto = new EnvironmentArtifactVeto();
    veto.setTargetEnvironment(targetEnvironment);
    veto.setReference(reference);
    veto.setVersion(version);
    veto.setComment(comment);
    return veto;
  }

  private DeliveryConfig toDeliveryConfig(Map<String, Object> manifest) {
    return objectMapper.convertValue(manifest, DeliveryConfig.class);
  }
}
