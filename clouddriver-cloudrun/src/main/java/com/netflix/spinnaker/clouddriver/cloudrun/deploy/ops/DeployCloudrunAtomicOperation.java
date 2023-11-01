/*
 * Copyright 2022 OpsMx Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.deploy.ops;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunJobExecutor;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.CloudrunServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.DeployCloudrunDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.exception.CloudrunOperationException;
import com.netflix.spinnaker.clouddriver.cloudrun.model.*;
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

public class DeployCloudrunAtomicOperation implements AtomicOperation<DeploymentResult> {

  private static final String BASE_PHASE = "DEPLOY";

  private static final Logger log = LoggerFactory.getLogger(DeployCloudrunAtomicOperation.class);

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Autowired CloudrunJobExecutor jobExecutor;

  @Autowired CloudrunLoadBalancerProvider provider;

  DeployCloudrunDescription description;

  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private final ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());

  private CloudrunYmlData ymlData = new CloudrunYmlData();

  public DeployCloudrunAtomicOperation(DeployCloudrunDescription description) {
    this.description = description;
  }

  public String deploy(String repositoryPath) {
    String project = description.getCredentials().getProject();
    String applicationDirectoryRoot = description.getApplicationDirectoryRoot();
    CloudrunServerGroupNameResolver serverGroupNameResolver =
        new CloudrunServerGroupNameResolver(
            project, description.getRegion(), description.getCredentials());
    String clusterName =
        serverGroupNameResolver.getClusterName(
            description.getApplication(), description.getStack(), description.getFreeFormDetails());
    String versionName =
        serverGroupNameResolver.resolveNextServerGroupName(
            description.getApplication(),
            description.getStack(),
            description.getFreeFormDetails(),
            description.getSuppressVersionString());
    List<String> configFiles = description.getConfigFiles();
    try {
      populateCloudrunYmlData(configFiles);
    } catch (Exception e) {
      log.error("Failed to populate the cloudrun yml data ", e);
      throw new CloudrunOperationException(
          "Failed to populate the cloudrun yml data " + e.getMessage());
    }
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
    getTask().updateStatus(BASE_PHASE, "Deploying version " + versionName + "...");
    try {
      jobExecutor.runCommand(deployCommand);
      success = "true";
    } catch (Exception e) {
      throw new CloudrunOperationException(
          "Failed to deploy to Cloud Run with command "
              + deployCommand
              + "exception "
              + e.getMessage());
    } finally {
      deleteFiles(writtenFullConfigFilePaths);
    }
    getTask().updateStatus(BASE_PHASE, "Done deploying version " + versionName + "...");
    return versionName;
  }

  private void populateCloudrunYmlData(List<String> configFiles) throws JsonProcessingException {

    for (String configFile : configFiles) {
      CloudrunService yamlObj = yamlReader.readValue(configFile, CloudrunService.class);
      if (yamlObj != null && yamlObj.getMetadata() != null && yamlObj.getSpec() != null) {
        ymlData.setKind(yamlObj.getKind());
        ymlData.setApiVersion(yamlObj.getApiVersion());

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

  private List<String> insertSpinnakerAppNameServiceNameVersionName(
      List<String> configFiles, String clusterName, String versionName) {

    return configFiles.stream()
        .map(
            (configFile) -> {
              try {
                CloudrunService yamlObj = yamlReader.readValue(configFile, CloudrunService.class);
                if (yamlObj != null && yamlObj.getMetadata() != null) {
                  CloudrunMetaData metadata = ymlData.getMetadata();
                  CloudrunSpec spec = ymlData.getSpec();
                  if (metadata != null && spec != null) {
                    if (spec.getTemplate() != null && spec.getTemplate().getMetadata() != null) {
                      CloudrunSpecTemplateMetadata specMetadata = spec.getTemplate().getMetadata();
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
                return yamlReader.writeValueAsString(ymlData);
              } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            })
        .collect(Collectors.toList());
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

  @Override
  public DeploymentResult operate(List priorOutputs) {

    String baseDir = description.getCredentials().getLocalRepositoryDirectory();
    String directoryPath = getFullDirectoryPath(baseDir);
    String serviceAccount = description.getCredentials().getServiceAccountEmail();
    String deployPath = directoryPath;
    String newVersionName;
    String success = "false";
    getTask().updateStatus(BASE_PHASE, "Initializing creation of version...");
    newVersionName = deploy(deployPath);
    String region = description.getRegion();
    DeploymentResult result = new DeploymentResult();
    StringBuffer sb = new StringBuffer();
    sb.append(region).append(":").append(newVersionName);
    result.setServerGroupNames(Arrays.asList(sb.toString()));
    Map<String, String> namesByRegion = new HashMap<>();
    namesByRegion.put(region, newVersionName);
    result.setServerGroupNameByRegion(namesByRegion);
    log.info(" region in deploy operation : " + region);
    log.info(" new version name in deploy operation : " + newVersionName);
    success = "true";
    return result;
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
