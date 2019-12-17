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
 */

package com.netflix.spinnaker.halyard.config.model.v1.node;

import com.netflix.spinnaker.halyard.config.model.v1.ha.HaServices;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** A DeploymentEnvironment is a location where Spinnaker is installed. */
@Data
@EqualsAndHashCode(callSuper = false)
public class DeploymentEnvironment extends Node {

  @Override
  public String getNodeName() {
    return "deploymentEnvironment";
  }

  public enum DeploymentType {
    Distributed(
        "Deploy Spinnaker with one server group and load balancer "
            + "per microservice, and a single instance of Redis acting as "
            + "Spinnaker's cache layer. This requires a cloud provider to deploy to."),
    LocalDebian(
        "Deploy Spinnaker locally (on the machine running the daemon) "
            + "using `apt-get` to fetch all the service's debian packages."),
    LocalGit(
        "Deploy Spinnaker locally (on the machine running the daemon) "
            + "using `git` to fetch all the service's code to be built & run."),
    BakeDebian(
        "Deploy Spinnaker locally but only with the necessary config "
            + "to be baked into a VM image later.");

    @Getter final String description;

    DeploymentType(String description) {
      this.description = description;
    }

    public static DeploymentType fromString(String name) {
      for (DeploymentType type : DeploymentType.values()) {
        if (type.toString().equalsIgnoreCase(name)) {
          return type;
        }
      }

      throw new IllegalArgumentException(
          "DeploymentType \""
              + name
              + "\" is not a valid choice. The options are: "
              + Arrays.toString(DeploymentType.values()));
    }
  }

  public enum Size {
    SMALL,
    MEDIUM,
    LARGE;

    public static Size fromString(String name) {
      for (Size type : Size.values()) {
        if (type.toString().equalsIgnoreCase(name)) {
          return type;
        }
      }

      throw new IllegalArgumentException(
          "Size \""
              + name
              + "\" is not a valid choice. The options are: "
              + Arrays.toString(Size.values()));
    }
  }

  public enum ImageVariant {
    SLIM("Based on an Alpine image"),
    UBUNTU("Based on Canonical's ubuntu:bionic image"),
    JAVA8("A variant of SLIM that uses the Java 8 runtime"),
    UBUNTU_JAVA8("A variant of UBUNTU that uses the Java 8 runtime");

    @Getter final String description;

    ImageVariant(String description) {
      this.description = description;
    }

    public static ImageVariant fromString(String name) {
      try {
        return ImageVariant.valueOf(name.toUpperCase().replace('-', '_'));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            String.format(
                "ImageVariant \"%s\" is not a valid choice. The options are: %s",
                name, Arrays.toString(ImageVariant.values())));
      }
    }

    public String getContainerSuffix() {
      return name().toLowerCase().replace("_", "-");
    }
  }

  private Size size = Size.SMALL;
  private DeploymentType type = DeploymentType.LocalDebian;
  private String accountName;
  private ImageVariant imageVariant = ImageVariant.SLIM;
  private Boolean bootstrapOnly;
  private Boolean updateVersions = true;
  private Consul consul = new Consul();
  private Vault vault = new Vault();
  private String location;
  private CustomSizing customSizing = new CustomSizing();
  private Map<String, List<SidecarConfig>> sidecars = new HashMap<>();
  private Map<String, List<Map>> initContainers = new HashMap<>();
  private Map<String, List<Map>> hostAliases = new HashMap<>();
  private Map<String, AffinityConfig> affinity = new HashMap<>();
  private Map<String, List<Toleration>> tolerations = new HashMap<>();
  private Map<String, String> nodeSelectors = new HashMap<>();
  private GitConfig gitConfig = new GitConfig();
  private LivenessProbeConfig livenessProbeConfig = new LivenessProbeConfig();

  @ValidForSpinnakerVersion(
      lowerBound = "1.10.0",
      tooLowMessage = "High availability services are not available prior to this release.")
  private HaServices haServices = new HaServices();

  public Boolean getUpdateVersions() {
    // default is true, even when unset
    return updateVersions == null ? true : updateVersions;
  }

  @Data
  public static class Consul {
    String address;
    boolean enabled;
  }

  @Data
  public static class Vault {
    String address;
    boolean enabled;
  }

  @Data
  public static class GitConfig {
    String upstreamUser = "spinnaker";
    String originUser;
  }

  @Data
  public static class LivenessProbeConfig {
    boolean enabled;
    Integer initialDelaySeconds;
  }
}
