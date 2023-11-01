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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunJobExecutor;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.CloudrunServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.exception.CloudrunOperationException;
import com.netflix.spinnaker.clouddriver.cloudrun.description.manifest.CloudrunDeployManifestDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.model.*;
import com.netflix.spinnaker.clouddriver.cloudrun.op.CloudrunManifestOperationResult;
import com.netflix.spinnaker.clouddriver.cloudrun.provider.view.CloudrunLoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
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

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Autowired CloudrunJobExecutor jobExecutor;

  @Autowired CloudrunLoadBalancerProvider provider;

  CloudrunDeployManifestDescription description;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private CloudrunYmlData ymlData = new CloudrunYmlData();

  public CloudrunDeployManifestOperation(CloudrunDeployManifestDescription description) {
    this.description = description;
  }

  public String deploy(String repositoryPath) {
    String project = description.getCredentials().getProject();
    String applicationDirectoryRoot = null;
    List<CloudrunService> configFiles = description.getManifests();
    Map<String, Artifact> allArtifacts = initializeArtifacts();
    try {
      populateCloudrunYmlData(configFiles);
    } catch (Exception e) {
      throw new CloudrunOperationException(
          "Failed to deploy manifest to Cloud Run with command " + e.getMessage());
    }
    List<String> modConfigFiles = null;
    if (allArtifacts != null && !allArtifacts.values().isEmpty()) {
      modConfigFiles = bindArtifacts(configFiles, allArtifacts.values());
    }
    CloudrunServerGroupNameResolver serverGroupNameResolver =
        new CloudrunServerGroupNameResolver(
            project, description.getRegion(), description.getCredentials());
    populateRegionFromManifest(configFiles);
    serverGroupNameResolver.setRegion(description.getRegion());
    String clusterName =
        serverGroupNameResolver.getClusterName(
            description.getApplication(), description.getStack(), description.getDetails());
    String versionName =
        serverGroupNameResolver.resolveNextServerGroupName(
            description.getApplication(), description.getStack(), description.getDetails(), false);
    modConfigFiles =
        insertSpinnakerAppNameServiceNameVersionName(configFiles, clusterName, versionName);
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

  private List<String> insertSpinnakerAppNameServiceNameVersionName(
      List<CloudrunService> configFiles, String clusterName, String versionName) {

    return configFiles.stream()
        .map(
            (configFile) -> {
              try {
                ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
                CloudrunService yamlObj = configFile;
                if (yamlObj != null) {
                  if (yamlObj.getMetadata() != null) {
                    CloudrunMetaData metadata = ymlData.getMetadata();
                    CloudrunSpec spec = ymlData.getSpec();
                    if (metadata != null && spec != null) {
                      if (spec.getTemplate() != null && spec.getTemplate().getMetadata() != null) {
                        CloudrunSpecTemplateMetadata specMetadata =
                            spec.getTemplate().getMetadata();
                        specMetadata.setName(versionName);
                        metadata.setName(clusterName);
                        CloudrunLoadBalancer loadBalancer =
                            provider.getLoadBalancer(description.getAccountName(), clusterName);
                        if (loadBalancer != null) {
                          insertTrafficPercent(spec, loadBalancer);
                        }
                      }
                    }
                    CloudrunMetadataAnnotations annotations = metadata.getAnnotations();
                    CloudrunMetadataLabels labels = metadata.getLabels();
                    if (annotations == null) {
                      CloudrunMetadataAnnotations metadataAnnotations =
                          new CloudrunMetadataAnnotations();
                      metadataAnnotations.setSpinnakerApplication(description.getApplication());
                      metadata.setAnnotations(metadataAnnotations);
                      description.setRegion(labels.getCloudGoogleapisComLocation());
                    } else if (annotations != null && labels != null) {
                      annotations.setSpinnakerApplication(description.getApplication());
                      description.setRegion(labels.getCloudGoogleapisComLocation());
                    }
                  }
                }
                return yamlReader.writeValueAsString(ymlData);
              } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            })
        .collect(Collectors.toList());
  }

  private List<String> bindArtifacts(
      List<CloudrunService> configFiles, Collection<Artifact> artifacts) {

    return configFiles.stream()
        .map(
            (configFile) -> {
              try {
                ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
                CloudrunService yamlObj = configFile;
                if (yamlObj != null && yamlObj.getMetadata() != null) {
                  CloudrunMetaData metadata = ymlData.getMetadata();
                  CloudrunSpec spec = ymlData.getSpec();
                  if (metadata != null && metadata != null) {
                    if (metadata.getAnnotations() != null && spec.getTemplate().getSpec() != null) {
                      CloudrunTemplateSpec specSpec = spec.getTemplate().getSpec();
                      CloudrunMetadataAnnotations annotations = metadata.getAnnotations();
                      bindTheRequiredArtifact(annotations, specSpec, artifacts);
                    }
                  }
                }
                return yamlReader.writeValueAsString(yamlObj);
              } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            })
        .collect(Collectors.toList());
  }

  private void bindTheRequiredArtifact(
      CloudrunMetadataAnnotations annotations,
      CloudrunTemplateSpec specSpec,
      Collection<Artifact> artifacts) {

    if (specSpec.getContainers() != null) {
      CloudrunSpecContainer[] containerArray = specSpec.getContainers();
      if (containerArray != null && !(containerArray.length == 0)) {
        CloudrunSpecContainer container = containerArray[0];
        for (Artifact artifact : artifacts) {
          if (artifact.getType().equals("docker/image")) {
            String cloudrunImage = (String) annotations.getClientKnativeDevUserImage();
            if (cloudrunImage != null) {
              String[] imageArray = cloudrunImage.split(":");
              String image = imageArray[0];
              if (image != null && artifact.getName() != null) {
                String[] imageArr = image.split("/");
                String[] artifactArr = artifact.getName().split("/");
                if (imageArr != null
                    && artifactArr != null
                    && imageArr.length > 0
                    && artifactArr.length > 0) {
                  String appImage = imageArr[imageArr.length - 1];
                  String artifactImage = artifactArr[artifactArr.length - 1];
                  if (appImage != null && artifactImage != null && appImage.equals(artifactImage)) {
                    annotations.setClientKnativeDevUserImage(artifact.getReference());
                    container.setImage(artifact.getReference());
                  } else {
                    throw new IllegalArgumentException(
                        String.format(
                            "The following required artifacts could not be bound: '%s'. "
                                + "Check that the Docker image name above matches the name used in the image field of your manifest. "
                                + "Failing the stage as this is likely a configuration error.",
                            ArtifactKey.fromArtifact(artifact)));
                  }
                }
              } else {
                throw new IllegalArgumentException(
                    String.format(
                        "The following required artifacts could not be bound: '%s'. "
                            + "Check that the Docker image name above matches the name used in the image field of your manifest. "
                            + "Failing the stage as this is likely a configuration error.",
                        ArtifactKey.fromArtifact(artifact)));
              }
            }
          } else {
            throw new IllegalArgumentException(
                String.format(
                    "The following required artifacts could not be bound: '%s'. "
                        + "Check that the Docker image name above matches the name used in the image field of your manifest. "
                        + "Failing the stage as this is likely a configuration error.",
                    ArtifactKey.fromArtifact(artifact)));
          }
        }
      }
    }
  }

  private void populateCloudrunYmlData(List<CloudrunService> configFiles)
      throws JsonProcessingException {

    for (CloudrunService configFile : configFiles) {
      CloudrunService yamlObj = configFile;
      if (yamlObj != null && yamlObj.getMetadata() != null && yamlObj.getSpec() != null) {
        ymlData.setKind(yamlObj.getKind());
        ymlData.setApiVersion(yamlObj.getApiVersion());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String metaDataJson = objectMapper.writeValueAsString(yamlObj.getMetadata());
        CloudrunMetaData cloudrunMetaData =
            objectMapper.readValue(metaDataJson, CloudrunMetaData.class);
        ymlData.setMetadata(cloudrunMetaData);
        String specJson = objectMapper.writeValueAsString(yamlObj.getSpec());
        CloudrunSpec spec = objectMapper.readValue(specJson, CloudrunSpec.class);
        ymlData.setSpec(spec);
      }
    }
  }

  private void populateRegionFromManifest(List<CloudrunService> configFiles) {

    for (CloudrunService configFile : configFiles) {
      CloudrunService yamlObj = configFile;
      if (yamlObj != null) {
        if (yamlObj.getMetadata() != null) {
          CloudrunMetaData metadata = ymlData.getMetadata();
          CloudrunMetadataLabels labels = metadata.getLabels();
          if (labels != null) {
            description.setRegion(labels.getCloudGoogleapisComLocation());
          }
        }
      }
    }
  }

  private void insertTrafficPercent(CloudrunSpec spec, CloudrunLoadBalancer loadBalancer) {

    List<CloudrunSpecTraffic> trafficTargets = new ArrayList<>();
    if (loadBalancer.getSplit() != null && loadBalancer.getSplit().getTrafficTargets() != null) {
      loadBalancer
          .getSplit()
          .getTrafficTargets()
          .forEach(
              trafficTarget -> {
                CloudrunSpecTraffic existingTrafficMap = new CloudrunSpecTraffic();
                existingTrafficMap.setPercent(trafficTarget.getPercent());
                existingTrafficMap.setRevisionName(trafficTarget.getRevisionName());
                trafficTargets.add(existingTrafficMap);
              });
      spec.setTraffic(trafficTargets.toArray(new CloudrunSpecTraffic[0]));
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
