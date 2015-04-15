/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.rosco.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName
import groovy.transform.CompileStatic
import groovy.transform.Immutable
import org.springframework.beans.factory.annotation.Value

/**
 * A request to bake a new AMI.
 *
 * @see BakeryController#createBake
 */
@Immutable(copyWith = true)
@CompileStatic
class BakeRequest {

  String user
  @JsonProperty("package") @SerializedName("package") String package_name
  CloudProviderType cloud_provider_type
  Label base_label
  OperatingSystem base_os
  String base_name
  String base_ami
  VmType vm_type
  StoreType store_type
  Boolean enhanced_networking
  String ami_name
  String ami_suffix

  static enum CloudProviderType {
    aws, docker, gce
  }

  static enum Label {
    release, candidate, previous, unstable, foundation
  }

  static enum PackageType {
    RPM('rpm', '-'),
    DEB('deb', '_')

    private final String packageType
    private final String versionDelimiter

    private PackageType(String packageType, String versionDelimiter) {
      this.packageType = packageType
      this.versionDelimiter = versionDelimiter
    }

    String getPackageType() {
      return this.packageType
    }

    String getVersionDelimiter() {
      return this.versionDelimiter
    }
  }

  static enum OperatingSystem {

    centos(PackageType.RPM), ubuntu(PackageType.DEB), trusty(PackageType.DEB)

    private final PackageType packageType
    private OperatingSystem(PackageType packageType) {
      this.packageType = packageType
    }

    PackageType getPackageType() {
      return packageType
    }
  }

  static enum VmType {
    pv, hvm
  }

  static enum StoreType {
    ebs, s3, docker
  }
}

