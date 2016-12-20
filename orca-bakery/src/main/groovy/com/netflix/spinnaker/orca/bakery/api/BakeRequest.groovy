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
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import groovy.transform.CompileStatic
import groovy.transform.Immutable

/**
 * A request to bake a new image.
 *
 * @see BakeryService#createBake
 */
@Immutable(copyWith = true)
@CompileStatic
class BakeRequest {
  private static final PropertyNamingStrategy.LowerCaseWithUnderscoresStrategy lowerCaseWithUnderscoresStrategy =
    new PropertyNamingStrategy.LowerCaseWithUnderscoresStrategy()

  static final Default = new BakeRequest(user: System.getProperty("user.name"),
                                         cloudProviderType: CloudProviderType.aws,
                                         baseLabel: Label.release,
                                         baseOs: "ubuntu")

  String user
  @JsonProperty("package") String packageName
  String buildHost
  String job
  String buildNumber
  String commitHash
  String buildInfoUrl
  CloudProviderType cloudProviderType
  Label baseLabel
  String baseOs
  String baseName
  String baseAmi
  VmType vmType
  StoreType storeType
  Boolean enhancedNetworking
  String amiName
  String amiSuffix

  private Map<String, Object> other = new HashMap<String, Object>()

  @JsonAnyGetter
  public Map<String, Object> other() {
    return other
  }

  @JsonAnySetter
  public void set(String name, Object value) {
    other.put(lowerCaseWithUnderscoresStrategy.translate(name), value)
  }

  static enum CloudProviderType {
    aws, azure, docker, gce, openstack, titus
  }

  static enum Label {
    release, candidate, previous, unstable, foundation
  }

  static enum VmType {
    pv, hvm
  }

  static enum StoreType {
    ebs, s3, docker
  }
}

