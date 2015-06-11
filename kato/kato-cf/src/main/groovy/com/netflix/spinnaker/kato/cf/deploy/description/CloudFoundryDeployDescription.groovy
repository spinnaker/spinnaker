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
package com.netflix.spinnaker.kato.cf.deploy.description

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.kato.cf.security.CloudFoundryAccountCredentials
import com.netflix.spinnaker.kato.deploy.DeployDescription
import groovy.transform.AutoClone
import groovy.transform.Canonical
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader

/**
 * Descriptor for a Cloud Foundry {@link DeployDescription}
 *
 * @author Greg Turnquist
 */
@AutoClone
@Canonical
class CloudFoundryDeployDescription implements DeployDescription {

  String api
  String org
  String space
  String application
  String artifact
  Resource artifactResource
  Integer memory = 512
  Boolean trustSelfSignedCerts = false  // switch to true for your own local CF instance
  Integer instances = null              // blank this out by default, letting the platform set the default
  List<String> urls = null              // blank this out by default
  List<String> domains = null           // blank this out by default

  @JsonIgnore
  CloudFoundryAccountCredentials credentials

  @JsonIgnore
  private final ResourceLoader resourceLoader = new DefaultResourceLoader()

  @JsonProperty("credentials")
  String getCredentialAccount() {
    this.credentials.name
  }

  void setArtifact(String artifact) {
    this.artifact = artifact
    if (new File(artifact).exists()) {
      this.artifactResource = resourceLoader.getResource('file://' + artifact)
    } else {
      this.artifactResource = resourceLoader.getResource(artifact)
    }
  }

}
