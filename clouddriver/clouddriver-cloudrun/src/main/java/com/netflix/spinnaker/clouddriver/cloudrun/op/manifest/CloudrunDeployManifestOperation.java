/*
 * Copyright 2022 OpsMx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.cloudrun.op.manifest;

import com.netflix.spinnaker.clouddriver.cloudrun.config.CloudrunConfigurationProperties;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.CloudrunServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.CloudrunServiceClient;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.exception.CloudrunOperationException;
import com.netflix.spinnaker.clouddriver.cloudrun.description.manifest.CloudrunDeployManifestDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunService;
import com.netflix.spinnaker.clouddriver.cloudrun.op.CloudrunManifestOperationResult;
import com.netflix.spinnaker.clouddriver.cloudrun.provider.view.CloudrunLoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class CloudrunDeployManifestOperation implements AtomicOperation<DeploymentResult> {

  private static final Logger log = LoggerFactory.getLogger(CloudrunDeployManifestOperation.class);

  private static final String OP_NAME = "DEPLOY_CLOUDRUN_MANIFEST";

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  CloudrunServiceClient serviceClient;

  @Autowired(required = false)
  CloudrunConfigurationProperties configurationProperties;

  @Autowired CloudrunLoadBalancerProvider provider;

  CloudrunDeployManifestDescription description;

  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  public CloudrunDeployManifestOperation(CloudrunDeployManifestDescription description) {
    this.description = description;
  }

  public String deploy() {
    String project = description.getCredentials().getProject();
    Map<String, Artifact> allArtifacts = initializeArtifacts();
    // Work on a copy of the manifest as a free-form map so that every field the user supplied
    // reaches Cloud Run; Spinnaker only overwrites the handful of fields it owns.
    List<Map<String, Object>> manifests = copyManifests(description.getManifests());
    CloudrunManifestEditor.requireExactlyOne(manifests);
    Map<String, Object> manifest = manifests.get(0);
    if (!allArtifacts.isEmpty()) {
      bindArtifacts(manifest, allArtifacts.values());
    }
    CloudrunManifestEditor.locationLabel(manifest).ifPresent(description::setRegion);
    CloudrunServerGroupNameResolver serverGroupNameResolver =
        new CloudrunServerGroupNameResolver(
            project, description.getRegion(), description.getCredentials());
    String clusterName =
        serverGroupNameResolver.getClusterName(
            description.getApplication(), description.getStack(), description.getDetails());
    String versionName =
        serverGroupNameResolver.resolveNextServerGroupName(
            description.getApplication(), description.getStack(), description.getDetails(), false);
    CloudrunManifestEditor.applySpinnakerFields(
        manifest,
        description.getApplication(),
        clusterName,
        versionName,
        provider.getLoadBalancer(description.getAccountName(), clusterName));

    getTask().updateStatus(OP_NAME, "Deploying manifest version " + versionName + "...");
    try {
      serviceClient().deployService(description.getRegion(), manifest);
    } catch (Exception e) {
      throw new CloudrunOperationException(
          "Failed to deploy manifest to Cloud Run: " + e.getMessage(), e);
    }
    getTask().updateStatus(OP_NAME, "Done deploying manifest version " + versionName + "...");
    return versionName;
  }

  private CloudrunServiceClient serviceClient() {
    if (serviceClient == null) {
      serviceClient =
          CloudrunServiceClient.forAccount(description.getCredentials(), configurationProperties);
    }
    return serviceClient;
  }

  private Map<String, Artifact> initializeArtifacts() {
    Map<String, Artifact> allArtifacts = new HashMap<>();
    if (!description.isEnableArtifactBinding()) {
      return allArtifacts;
    }
    // Required artifacts are explicitly set in stage configuration
    if (description.getRequiredArtifacts() != null) {
      description
          .getRequiredArtifacts()
          .forEach(a -> allArtifacts.putIfAbsent(getArtifactKey(a), a));
    }
    return allArtifacts;
  }

  private String getArtifactKey(Artifact artifact) {
    return String.format(
        "[%s]-[%s]-[%s]",
        artifact.getType(),
        artifact.getName(),
        artifact.getLocation() != null ? artifact.getLocation() : "");
  }

  @Override
  public CloudrunManifestOperationResult operate(List priorOutputs) {

    String newVersionName;
    getTask().updateStatus(OP_NAME, "Initializing creation of manifest version...");
    newVersionName = deploy();
    String region = description.getRegion();
    CloudrunManifestOperationResult result = new CloudrunManifestOperationResult();
    result.addManifest(description.getManifests().get(0));
    // Orca reads these to force a cache refresh of the new revision before later stages resolve it
    result.getServerGroupNames().add(region + ":" + newVersionName);
    result.getServerGroupNameByRegion().put(region, newVersionName);
    log.info(" region in deploy manifest operation : " + region);
    log.info(" new version name in deploy manifest operation : " + newVersionName);
    return result;
  }

  private List<Map<String, Object>> copyManifests(List<CloudrunService> services) {
    List<Map<String, Object>> manifests = new ArrayList<>();
    for (CloudrunService service : services) {
      if (service == null || service.getMetadata() == null || service.getSpec() == null) {
        continue;
      }
      Map<String, Object> manifest = new LinkedHashMap<>();
      manifest.put("apiVersion", service.getApiVersion());
      manifest.put("kind", service.getKind());
      // status is output only, so it is intentionally not carried over
      manifest.put("metadata", deepCopy(service.getMetadata()));
      manifest.put("spec", deepCopy(service.getSpec()));
      manifests.add(manifest);
    }
    return manifests;
  }

  private Map<String, Object> deepCopy(Map<String, Object> source) {
    return objectMapper.convertValue(source, new TypeReference<LinkedHashMap<String, Object>>() {});
  }

  private void bindArtifacts(Map<String, Object> manifest, Collection<Artifact> artifacts) {
    List<Map<String, Object>> containers = CloudrunManifestEditor.containers(manifest);
    for (Artifact artifact : artifacts) {
      boolean bound = false;
      if ("docker/image".equals(artifact.getType()) && artifact.getName() != null) {
        for (Map<String, Object> container : containers) {
          Object image = container.get("image");
          if (image instanceof String
              && lastPathSegment(imageWithoutTagOrDigest((String) image))
                  .equals(lastPathSegment(artifact.getName()))) {
            container.put("image", artifact.getReference());
            bound = true;
          }
        }
      }
      if (!bound) {
        throw new IllegalArgumentException(
            String.format(
                "The following required artifacts could not be bound: '%s'. "
                    + "Check that the Docker image name above matches the name used in the image field of your manifest. "
                    + "Failing the stage as this is likely a configuration error.",
                ArtifactKey.fromArtifact(artifact)));
      }
    }
  }

  private static String imageWithoutTagOrDigest(String image) {
    String withoutDigest = image.split("@", 2)[0];
    int lastColon = withoutDigest.lastIndexOf(':');
    // a colon before the last slash is a registry port, not a tag
    return lastColon > withoutDigest.lastIndexOf('/')
        ? withoutDigest.substring(0, lastColon)
        : withoutDigest;
  }

  private static String lastPathSegment(String name) {
    return name.substring(name.lastIndexOf('/') + 1);
  }
}
