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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunJobExecutor;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.CloudrunServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.exception.CloudrunOperationException;
import com.netflix.spinnaker.clouddriver.cloudrun.description.manifest.CloudrunDeployManifestDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunService;
import com.netflix.spinnaker.clouddriver.cloudrun.op.CloudrunManifestOperationResult;
import com.netflix.spinnaker.clouddriver.cloudrun.provider.view.CloudrunLoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.yaml.YamlHelper;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class CloudrunDeployManifestOperation implements AtomicOperation<DeploymentResult> {

  private static final Logger log = LoggerFactory.getLogger(CloudrunDeployManifestOperation.class);

  private static final String OP_NAME = "DEPLOY_CLOUDRUN_MANIFEST";

  private static final String SPINNAKER_APPLICATION_ANNOTATION = "spinnaker/application";

  private static final String LOCATION_LABEL = "cloud.googleapis.com/location";

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Autowired CloudrunJobExecutor jobExecutor;

  @Autowired CloudrunLoadBalancerProvider provider;

  CloudrunDeployManifestDescription description;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final ObjectMapper yamlMapper =
      new ObjectMapper(YAMLFactory.builder().loaderOptions(YamlHelper.getLoaderOptions()).build());

  public CloudrunDeployManifestOperation(CloudrunDeployManifestDescription description) {
    this.description = description;
  }

  public String deploy(String repositoryPath) {
    String project = description.getCredentials().getProject();
    String applicationDirectoryRoot = null;
    Map<String, Artifact> allArtifacts = initializeArtifacts();
    // Work on copies of the manifests as free-form maps so that every field the user supplied
    // reaches gcloud; Spinnaker only overwrites the handful of fields it owns.
    List<Map<String, Object>> manifests = copyManifests(description.getManifests());
    if (!allArtifacts.isEmpty()) {
      manifests.forEach(manifest -> bindArtifacts(manifest, allArtifacts.values()));
    }
    populateRegionFromManifest(manifests);
    CloudrunServerGroupNameResolver serverGroupNameResolver =
        new CloudrunServerGroupNameResolver(
            project, description.getRegion(), description.getCredentials());
    String clusterName =
        serverGroupNameResolver.getClusterName(
            description.getApplication(), description.getStack(), description.getDetails());
    String versionName =
        serverGroupNameResolver.resolveNextServerGroupName(
            description.getApplication(), description.getStack(), description.getDetails(), false);
    List<String> modConfigFiles =
        insertSpinnakerAppNameServiceNameVersionName(manifests, clusterName, versionName);
    List<String> writtenFullConfigFilePaths =
        writeConfigFiles(modConfigFiles, repositoryPath, applicationDirectoryRoot);
    String region = description.getRegion();

    List<String> deployCommand = new ArrayList<>();
    deployCommand.add("gcloud");
    deployCommand.add("run");
    deployCommand.add("services");
    deployCommand.add("replace");
    deployCommand.add(writtenFullConfigFilePaths.stream().collect(Collectors.joining("")));
    deployCommand.add("--region=" + region);
    deployCommand.add("--project=" + project);

    String success = "false";
    getTask().updateStatus(OP_NAME, "Deploying manifest version " + versionName + "...");
    try {
      jobExecutor.runCommand(deployCommand);
      success = "true";
    } catch (Exception e) {
      throw new CloudrunOperationException(
          "Failed to deploy manifest to Cloud Run with command "
              + deployCommand
              + "exception "
              + e.getMessage());
    } finally {
      deleteFiles(writtenFullConfigFilePaths);
    }
    getTask().updateStatus(OP_NAME, "Done deploying manifest version " + versionName + "...");
    return versionName;
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

    String baseDir = description.getCredentials().getLocalRepositoryDirectory();
    String directoryPath = getFullDirectoryPath(baseDir);
    String deployPath = directoryPath;
    String newVersionName;
    String success = "false";
    getTask().updateStatus(OP_NAME, "Initializing creation of manifest version...");
    newVersionName = deploy(directoryPath);
    String region = description.getRegion();
    CloudrunManifestOperationResult result = new CloudrunManifestOperationResult();
    result.addManifest(description.getManifests().get(0));
    log.info(" region in deploy manifest operation : " + region);
    log.info(" new version name in deploy manifest operation : " + newVersionName);
    success = "true";
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

  private List<String> insertSpinnakerAppNameServiceNameVersionName(
      List<Map<String, Object>> manifests, String clusterName, String versionName) {

    return manifests.stream()
        .map(
            manifest -> {
              Map<String, Object> metadata = childMap(manifest, "metadata");
              Map<String, Object> spec = childMap(manifest, "spec");
              metadata.put("name", clusterName);
              childMap(metadata, "annotations")
                  .put(SPINNAKER_APPLICATION_ANNOTATION, description.getApplication());
              childMap(childMap(spec, "template"), "metadata").put("name", versionName);
              CloudrunLoadBalancer loadBalancer =
                  provider.getLoadBalancer(description.getAccountName(), clusterName);
              if (loadBalancer != null) {
                insertTrafficPercent(spec, loadBalancer);
              }
              try {
                return yamlMapper.writeValueAsString(manifest);
              } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            })
        .collect(Collectors.toList());
  }

  private void bindArtifacts(Map<String, Object> manifest, Collection<Artifact> artifacts) {
    List<Map<String, Object>> containers = containers(manifest);
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

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> containers(Map<String, Object> manifest) {
    Object spec = manifest.get("spec");
    Object template = spec instanceof Map ? ((Map<String, Object>) spec).get("template") : null;
    Object templateSpec =
        template instanceof Map ? ((Map<String, Object>) template).get("spec") : null;
    Object containers =
        templateSpec instanceof Map ? ((Map<String, Object>) templateSpec).get("containers") : null;
    List<Map<String, Object>> result = new ArrayList<>();
    if (containers instanceof List) {
      for (Object container : (List<Object>) containers) {
        if (container instanceof Map) {
          result.add((Map<String, Object>) container);
        }
      }
    }
    return result;
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

  @SuppressWarnings("unchecked")
  private static Map<String, Object> childMap(Map<String, Object> parent, String key) {
    Object child = parent.get(key);
    if (child == null) {
      child = new LinkedHashMap<String, Object>();
      parent.put(key, child);
    }
    if (!(child instanceof Map)) {
      throw new IllegalArgumentException(
          String.format("Expected '%s' in the manifest to be an object", key));
    }
    return (Map<String, Object>) child;
  }

  @SuppressWarnings("unchecked")
  private void populateRegionFromManifest(List<Map<String, Object>> manifests) {
    for (Map<String, Object> manifest : manifests) {
      Object labels = childMap(manifest, "metadata").get("labels");
      if (labels instanceof Map) {
        Object location = ((Map<String, Object>) labels).get(LOCATION_LABEL);
        if (location != null) {
          description.setRegion(location.toString());
        }
      }
    }
  }

  private void insertTrafficPercent(Map<String, Object> spec, CloudrunLoadBalancer loadBalancer) {

    List<Map<String, Object>> trafficTargets = new ArrayList<>();
    if (loadBalancer.getSplit() != null && loadBalancer.getSplit().getTrafficTargets() != null) {
      loadBalancer
          .getSplit()
          .getTrafficTargets()
          .forEach(
              trafficTarget -> {
                Map<String, Object> existingTrafficMap = new LinkedHashMap<>();
                existingTrafficMap.put("percent", trafficTarget.getPercent());
                existingTrafficMap.put("revisionName", trafficTarget.getRevisionName());
                trafficTargets.add(existingTrafficMap);
              });
      spec.put("traffic", trafficTargets);
    }
  }

  public static void deleteFiles(List<String> paths) {
    paths.forEach(
        path -> {
          try {
            new File(path).delete();
          } catch (Exception e) {
            throw new CloudrunOperationException("Could not delete config file: ${e.getMessage()}");
          }
        });
  }

  public static List<String> writeConfigFiles(
      List<String> configFiles, String repositoryPath, String applicationDirectoryRoot) {
    if (configFiles == null) {
      return Collections.<String>emptyList();
    } else {
      return configFiles.stream()
          .map(
              (configFile) -> {
                Path path =
                    generateRandomRepositoryFilePath(repositoryPath, applicationDirectoryRoot);
                try {
                  File targetFile = new File(path.toString());
                  FileUtils.writeStringToFile(targetFile, configFile, StandardCharsets.UTF_8);
                } catch (Exception e) {
                  throw new CloudrunOperationException(
                      "Could not write config file: ${e.getMessage()}");
                }
                return path.toString();
              })
          .collect(Collectors.toList());
    }
  }

  public static Path generateRandomRepositoryFilePath(
      String repositoryPath, String applicationDirectoryRoot) {
    String name = UUID.randomUUID().toString();
    String filePath = applicationDirectoryRoot != null ? applicationDirectoryRoot : ".";
    StringBuilder sb = new StringBuilder(name).append(".yaml");
    return Paths.get(repositoryPath, filePath, sb.toString());
  }

  public static String getFullDirectoryPath(String localRepositoryDirectory) {
    return Paths.get(localRepositoryDirectory).toString();
  }
}
