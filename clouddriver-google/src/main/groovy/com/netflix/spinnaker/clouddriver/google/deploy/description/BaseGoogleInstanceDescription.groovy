/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.description

import com.netflix.spinnaker.clouddriver.google.model.GoogleDisk
import com.netflix.spinnaker.clouddriver.google.model.GoogleLabeledResource
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import groovy.transform.AutoClone
import groovy.transform.Canonical
import groovy.transform.ToString

@AutoClone
@Canonical
@ToString(includeNames = true)
class BaseGoogleInstanceDescription extends AbstractGoogleCredentialsDescription implements GoogleLabeledResource {
  String instanceType
  String minCpuPlatform
  List<GoogleDisk> disks
  Map<String, String> instanceMetadata
  List<String> tags
  Map<String, String> labels
  String network
  String subnet
  // This will be treated as true if it is null to preserve backwards-compatibility with existing pipelines.
  Boolean associatePublicIpAddress
  Boolean canIpForward
  String serviceAccountEmail
  List<String> authScopes
  Boolean preemptible
  Boolean automaticRestart
  OnHostMaintenance onHostMaintenance
  // Secure boot helps protect your VM instances against boot-level and kernel-level malware and rootkits.
  // Supported only for Shielded VMs
  Boolean enableSecureBoot;
  // Virtual Trusted Platform Module (vTPM) validates your guest VM pre-boot and boot integrity,
  // and offers key generation and protection.
  // Supported only for Shielded VMs
  Boolean enableVtpm;
  // Integrity monitoring lets you monitor and verify the runtime boot integrity of your shielded VM instances using Stackdriver reports.
  // Note: requires vTPM to be enabled.
  // Supported only for Shielded VMs
  Boolean enableIntegrityMonitoring;

  // Unique disk device name addressable by a Linux OS in /dev/disk/by-id/google-* in the running instance.
  // Used to reference disk for mounting, resizing, etc.
  // Only applicable for persistent disks.
  String baseDeviceName

  // We support passing the image to deploy as either a string or an artifact, but default to
  // the string for backwards-compatibility
  ImageSource imageSource = ImageSource.STRING
  String image
  Artifact imageArtifact

  String accountName

  // The source of the image to deploy
  // ARTIFACT: An artifact of type gce/image stored in imageArtifact
  // STRING:   A string representing a GCE image name in the current
  //           project, stored in image
  enum ImageSource {
    ARTIFACT, STRING
  }

  enum OnHostMaintenance {
    MIGRATE, TERMINATE
  }
}
