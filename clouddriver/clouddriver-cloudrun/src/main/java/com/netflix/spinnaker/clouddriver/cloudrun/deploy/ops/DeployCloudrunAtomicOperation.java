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
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.CloudrunServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.CloudrunServiceClient;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.DeployCloudrunDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.exception.CloudrunOperationException;
import com.netflix.spinnaker.clouddriver.cloudrun.op.manifest.CloudrunManifestEditor;
import com.netflix.spinnaker.clouddriver.cloudrun.provider.view.CloudrunLoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.kork.yaml.YamlHelper;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class DeployCloudrunAtomicOperation implements AtomicOperation<DeploymentResult> {

  private static final String BASE_PHASE = "DEPLOY";

  private static final Logger log = LoggerFactory.getLogger(DeployCloudrunAtomicOperation.class);

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  CloudrunServiceClient serviceClient;

  @Autowired CloudrunLoadBalancerProvider provider;

  DeployCloudrunDescription description;

  private final ObjectMapper yamlReader =
      new ObjectMapper(YAMLFactory.builder().loaderOptions(YamlHelper.getLoaderOptions()).build());

  public DeployCloudrunAtomicOperation(DeployCloudrunDescription description) {
    this.description = description;
  }

  public String deploy() {
    String project = description.getCredentials().getProject();
    List<Map<String, Object>> manifests;
    try {
      manifests = parseManifests(description.getConfigFiles());
    } catch (Exception e) {
      log.error("Failed to parse the cloudrun config files ", e);
      throw new CloudrunOperationException(
          "Failed to parse the cloudrun config files " + e.getMessage());
    }
    CloudrunManifestEditor.requireExactlyOne(manifests);
    Map<String, Object> manifest = manifests.get(0);
    CloudrunManifestEditor.locationLabel(manifest).ifPresent(description::setRegion);
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
    CloudrunManifestEditor.applySpinnakerFields(
        manifest,
        description.getApplication(),
        clusterName,
        versionName,
        provider.getLoadBalancer(description.getAccountName(), clusterName));

    getTask().updateStatus(BASE_PHASE, "Deploying version " + versionName + "...");
    try {
      serviceClient().deployService(description.getRegion(), manifest);
    } catch (Exception e) {
      throw new CloudrunOperationException("Failed to deploy to Cloud Run: " + e.getMessage(), e);
    }
    getTask().updateStatus(BASE_PHASE, "Done deploying version " + versionName + "...");
    return versionName;
  }

  private CloudrunServiceClient serviceClient() {
    if (serviceClient == null) {
      serviceClient = CloudrunServiceClient.forAccount(description.getCredentials());
    }
    return serviceClient;
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

  @Override
  public DeploymentResult operate(List priorOutputs) {

    String newVersionName;
    getTask().updateStatus(BASE_PHASE, "Initializing creation of version...");
    newVersionName = deploy();
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
    return result;
  }
}
