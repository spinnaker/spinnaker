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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunJobExecutor;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.CloudrunServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.DeployCloudrunDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.exception.CloudrunOperationException;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudrun.op.manifest.CloudrunManifestEditor;
import com.netflix.spinnaker.clouddriver.cloudrun.provider.view.CloudrunLoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
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

public class DeployCloudrunAtomicOperation implements AtomicOperation<DeploymentResult> {

  private static final String BASE_PHASE = "DEPLOY";

  private static final Logger log = LoggerFactory.getLogger(DeployCloudrunAtomicOperation.class);

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Autowired CloudrunJobExecutor jobExecutor;

  @Autowired CloudrunLoadBalancerProvider provider;

  DeployCloudrunDescription description;

  private final ObjectMapper yamlReader =
      new ObjectMapper(YAMLFactory.builder().loaderOptions(YamlHelper.getLoaderOptions()).build());

  public DeployCloudrunAtomicOperation(DeployCloudrunDescription description) {
    this.description = description;
  }

  public String deploy(String repositoryPath) {
    String project = description.getCredentials().getProject();
    String applicationDirectoryRoot = description.getApplicationDirectoryRoot();
    List<Map<String, Object>> manifests;
    try {
      manifests = parseManifests(description.getConfigFiles());
    } catch (Exception e) {
      log.error("Failed to parse the cloudrun config files ", e);
      throw new CloudrunOperationException(
          "Failed to parse the cloudrun config files " + e.getMessage());
    }
    CloudrunManifestEditor.requireExactlyOne(manifests);
    CloudrunManifestEditor.locationLabel(manifests.get(0)).ifPresent(description::setRegion);
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

  private List<Map<String, Object>> parseManifests(List<String> configFiles)
      throws JsonProcessingException {
    List<Map<String, Object>> manifests = new ArrayList<>();
    for (String configFile : configFiles == null ? List.<String>of() : configFiles) {
      Map<String, Object> manifest =
          yamlReader.readValue(configFile, new TypeReference<LinkedHashMap<String, Object>>() {});
      if (manifest != null && manifest.get("metadata") != null && manifest.get("spec") != null) {
        // status is output only, so it is intentionally not carried over
        manifest.remove("status");
        manifests.add(manifest);
      }
    }
    return manifests;
  }

  private List<String> insertSpinnakerAppNameServiceNameVersionName(
      List<Map<String, Object>> manifests, String clusterName, String versionName) {

    return manifests.stream()
        .map(
            manifest -> {
              CloudrunLoadBalancer loadBalancer =
                  provider.getLoadBalancer(description.getAccountName(), clusterName);
              CloudrunManifestEditor.applySpinnakerFields(
                  manifest, description.getApplication(), clusterName, versionName, loadBalancer);
              try {
                return yamlReader.writeValueAsString(manifest);
              } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            })
        .collect(Collectors.toList());
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
