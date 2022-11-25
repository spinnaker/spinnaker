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

  public CloudrunDeployManifestOperation(CloudrunDeployManifestDescription description) {
    this.description = description;
  }

  public String deploy(String repositoryPath) {
    String project = description.getCredentials().getProject();
    String applicationDirectoryRoot = null;
    List<CloudrunService> configFiles = description.getManifests();
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
    List<String> modConfigFiles =
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
                    LinkedHashMap<String, Object> metadataMap = yamlObj.getMetadata();
                    LinkedHashMap<String, Object> specMap = yamlObj.getSpec();
                    if (metadataMap != null && specMap != null) {
                      if (specMap.get("template") != null
                          && ((LinkedHashMap<String, Object>) specMap.get("template"))
                                  .get("metadata")
                              != null) {
                        LinkedHashMap<String, Object> specMetadataMap =
                            (LinkedHashMap<String, Object>)
                                ((LinkedHashMap<String, Object>) specMap.get("template"))
                                    .get("metadata");
                        specMetadataMap.put("name", versionName);
                        metadataMap.put("name", clusterName);
                        CloudrunLoadBalancer loadBalancer =
                            provider.getLoadBalancer(description.getAccountName(), clusterName);
                        if (loadBalancer != null) {
                          insertTrafficPercent(specMap, loadBalancer);
                        }
                      }
                    }
                    LinkedHashMap<String, Object> annotationsMap =
                        (LinkedHashMap<String, Object>) metadataMap.get("annotations");
                    LinkedHashMap<String, Object> labelsMap =
                        (LinkedHashMap<String, Object>) metadataMap.get("labels");
                    if (annotationsMap == null) {
                      Map<String, Object> tempMap = new LinkedHashMap<>();
                      tempMap.put("spinnaker/application", description.getApplication());
                      metadataMap.put("annotations", tempMap);
                      description.setRegion(
                          (String) labelsMap.get("cloud.googleapis.com/location"));
                    } else if (annotationsMap != null && labelsMap != null) {
                      annotationsMap.put("spinnaker/application", description.getApplication());
                      description.setRegion(
                          (String) labelsMap.get("cloud.googleapis.com/location"));
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

  private void populateRegionFromManifest(List<CloudrunService> configFiles) {

    for (CloudrunService configFile : configFiles) {
      CloudrunService yamlObj = configFile;
      if (yamlObj != null) {
        if (yamlObj.getMetadata() != null) {
          LinkedHashMap<String, Object> metadataMap = yamlObj.getMetadata();
          LinkedHashMap<String, Object> labelsMap =
              (LinkedHashMap<String, Object>) metadataMap.get("labels");
          if (labelsMap != null) {
            description.setRegion((String) labelsMap.get("cloud.googleapis.com/location"));
          }
        }
      }
    }
  }

  private void insertTrafficPercent(
      LinkedHashMap<String, Object> specMap, CloudrunLoadBalancer loadBalancer) {

    List<Map<String, Object>> trafficPercentList = new ArrayList<>();
    if (loadBalancer.getSplit() != null && loadBalancer.getSplit().getTrafficTargets() != null) {
      loadBalancer
          .getSplit()
          .getTrafficTargets()
          .forEach(
              trafficTarget -> {
                Map<String, Object> existingTrafficMap = new LinkedHashMap<>();
                existingTrafficMap.put("percent", trafficTarget.getPercent());
                existingTrafficMap.put("revisionName", trafficTarget.getRevisionName());
                trafficPercentList.add(existingTrafficMap);
              });
      specMap.put("traffic", trafficPercentList);
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
