/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.rosco.providers.proxmox.config

import com.netflix.spinnaker.rosco.api.BakeOptions
import groovy.transform.AutoClone
import groovy.transform.AutoCloneStyle
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty('proxmox.enabled')
@ComponentScan('com.netflix.spinnaker.rosco.providers.proxmox')
class RoscoProxmoxConfiguration {

  static class ProxmoxBakeryDefaults {
    /** Proxmox API URL, e.g. {@code https://pve01.example.com:8006/api2/json}. */
    String proxmoxUrl
    /** Proxmox username, e.g. {@code root@pam} or {@code packer@pve}. */
    String username
    /** Proxmox password. Mutually exclusive with {@code apiToken}. */
    String password
    /**
     * Proxmox API token in the form {@code user@realm!tokenid=secret}. When set, {@code password}
     * is ignored.
     */
    String apiToken
    /** Default node to run builds on. Can be overridden per base image. */
    String node
    /** Storage pool for the cloned disk, e.g. {@code local-lvm}. */
    String storage = 'local-lvm'
    /** Storage pool for the ephemeral cloud-init ISO, e.g. {@code local}. */
    String cloudInitStorage = 'local'
    /** Skip TLS verification — useful for self-signed Proxmox certificates. */
    boolean insecureSkipTlsVerify = false
    String templateFile
    List<ProxmoxOperatingSystemVirtualizationSettings> baseImages = []
  }

  static class ProxmoxOperatingSystemVirtualizationSettings {
    BakeOptions.BaseImage baseImage
    ProxmoxVirtualizationSettings virtualizationSettings
  }

  @AutoClone(style = AutoCloneStyle.SIMPLE)
  static class ProxmoxVirtualizationSettings {
    /** VMID of the cloud-init template to clone. Required. */
    int cloneVmId
    /** Node override for this base image. Falls back to {@code bakeryDefaults.node}. */
    String node
    /** SSH username for the cloud-init user (default {@code ubuntu}). */
    String sshUsername = 'ubuntu'
    int cores = 1
    int memory = 512
  }

  @Bean
  @ConfigurationProperties('proxmox.bakery-defaults')
  ProxmoxBakeryDefaults proxmoxBakeryDefaults() {
    new ProxmoxBakeryDefaults()
  }
}
