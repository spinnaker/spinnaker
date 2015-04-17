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

import groovy.transform.CompileStatic
import groovy.transform.Immutable
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName
import com.netflix.spinnaker.orca.pipeline.util.OperatingSystem

/**
 * A request to bake a new AMI.
 *
 * @see BakeryService#createBake
 */
@Immutable(copyWith = true)
@CompileStatic
class BakeRequest {
  static final Default = new BakeRequest(System.getProperty("user.name"), null, null, null, null, CloudProviderType.aws, Label.release, OperatingSystem.ubuntu, null, null, null, null, null, null, null)

  String user
  @JsonProperty("package") @SerializedName("package") String packageName
  String buildHost
  String job
  String buildNumber
  CloudProviderType cloudProviderType
  Label baseLabel
  OperatingSystem baseOs
  String baseName
  String baseAmi
  VmType vmType
  StoreType storeType
  Boolean enhancedNetworking
  String amiName
  String amiSuffix

  static enum CloudProviderType {
    aws, docker, gce
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

