/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.bakery.api

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import static com.fasterxml.jackson.databind.PropertyNamingStrategy.*
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import groovy.transform.CompileStatic
import groovy.transform.Immutable

/**
 * A request to bake a new image.
 *
 * @see BakeryService#createBake
 */
@Immutable(
  copyWith = true,
  knownImmutables = ["other"] // fletch: this is a hack since an upgrade of Groovy started replacing "other" with an unmodifiable map breaking @JsonAnySetter
)
@CompileStatic
class BakeRequest {
  private static final PropertyNamingStrategyBase namingStrategy = new SnakeCaseStrategy()

  static final Default = new BakeRequest(user: System.getProperty("user.name"),
                                         cloudProviderType: CloudProviderType.aws,
                                         baseLabel: "release",
                                         baseOs: "ubuntu")

  String user
  @JsonProperty("package") String packageName
  List<Artifact> packageArtifacts
  String buildHost
  String job
  String buildNumber
  String commitHash
  String buildInfoUrl
  CloudProviderType cloudProviderType
  String baseLabel
  String baseOs
  String baseName
  String baseAmi
  VmType vmType
  StoreType storeType
  Boolean enhancedNetworking
  String amiName
  String amiSuffix

  @JsonInclude(JsonInclude.Include.NON_NULL)
  Integer rootVolumeSize

  @JsonIgnore
  Map<String, Object> other = new HashMap<>()

  @JsonAnyGetter
  public Map<String, Object> other() {
    return other
  }

  @JsonAnySetter
  public void set(String name, Object value) {
    other.put(namingStrategy.translate(name), value)
  }

  static enum CloudProviderType {
    aws, azure, docker, gce, openstack, titus, oracle, alicloud, huaweicloud
  }

  static enum VmType {
    pv, hvm
  }

  static enum StoreType {
    ebs, s3, docker
  }
}

