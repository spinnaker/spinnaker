/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service;


import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.OrcaProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;
import retrofit.http.*;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
abstract public class OrcaService extends SpringService<OrcaService.Orca> {
  @Autowired
  OrcaProfileFactory orcaProfileFactory;

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.ORCA;
  }

  @Override
  public Type getType() {
    return Type.ORCA;
  }

  @Override
  public Class<Orca> getEndpointClass() {
    return Orca.class;
  }

  @Override
  public List<Profile> getProfiles(DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    List<Profile> profiles = super.getProfiles(deploymentConfiguration, endpoints);
    String filename = "orca.yml";

    String path = Paths.get(getConfigOutputPath(), filename).toString();
    Profile profile = orcaProfileFactory.getProfile(filename, path, deploymentConfiguration, endpoints);

    profiles.add(profile);
    return profiles;
  }

  public interface Orca {
    @Headers("Content-type: application/context+json")
    @POST("/ops")
    Map<String, Object> submitTask(@Body Map task);

    @POST("/orchestrate")
    Map<String, Object> orchestrate(@Body Map pipeline);

    @GET("/{id}")
    Map<String, Object> getRef(@Path(encode = false, value = "id") String id);

    @GET("/executions/activeByInstance")
    Map<String, ActiveExecutions> getActiveExecutions();

    @POST("/admin/instance/enabled")
    Response setInstanceStatusEnabled(@Body Map<String, String> request);

    @GET("/resolvedEnv")
    Map<String, String> resolvedEnv();

    @GET("/health")
    SpringHealth health();

    @Data
    class ActiveExecutions {
      boolean overdue;
      int count;
    }
  }

  @EqualsAndHashCode(callSuper = true)
  @Data
  public static class Settings extends SpringServiceSettings {
    Integer port = 8083;
    // Address is how the service is looked up.
    String address = "localhost";
    // Host is what's bound to by the service.
    String host = "0.0.0.0";
    String scheme = "http";
    String healthEndpoint = "/health";
    Boolean enabled = true;
    Boolean safeToUpdate = true;
    Boolean monitored = true;
    Boolean sidecar = false;
    Integer targetSize = 1;
    Boolean skipLifeCycleManagement = false;
    Map<String, String> env = new HashMap<>();

    public Settings() {}

    public Settings(List<String> profiles) {
      setProfiles(profiles);
    }
  }
}
