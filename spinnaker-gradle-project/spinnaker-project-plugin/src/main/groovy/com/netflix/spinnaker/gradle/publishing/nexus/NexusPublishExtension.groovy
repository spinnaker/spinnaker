/*
 * Copyright 2021 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.gradle.publishing.nexus

import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider;

class NexusPublishExtension {
  protected Project project

  final Property<Boolean> enabled
  final Property<String> nexusStagingUrl
  final Property<String> nexusSnapshotUrl
  final Property<String> nexusStagingProfileId

  NexusPublishExtension(Project project) {
    this.project = project
    ObjectFactory props = project.objects
    enabled = props.property(Boolean).convention(false)
    nexusStagingUrl = props.property(String).convention("https://s01.oss.sonatype.org/service/local/")
    nexusSnapshotUrl = props.property(String).convention("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    nexusStagingProfileId = props.property(String).convention("b6b58aed9c738")
  }

  Provider<Boolean> enabled() {
    return withSysProp(enabled, Boolean, "nexusPublishEnabled")
  }

  Provider<String> nexusStagingUrl() {
    return withSysProp(nexusStagingUrl, String, "nexusStagingUrl")
  }

  Provider<String> nexusSnapshotUrl() {
    return withSysProp(nexusSnapshotUrl, String, "nexusSnapshotUrl")
  }

  Provider<String> nexusStagingProfileId() {
    return withSysProp(nexusStagingProfileId, String, "nexusStagingProfileId")
  }

  String pgpSigningKey() {
    return projectProperty(String, "nexusPgpSigningKey")
  }

  String pgpSigningPassword() {
    return projectProperty(String, "nexusPgpSigningPassword")
  }

  String nexusUsername() {
    return projectProperty(String, "nexusUsername")
  }

  String nexusPassword() {
    return projectProperty(String, "nexusPassword")
  }

  protected <T> Provider<T> withSysProp(Property<T> property, Class<T> type, String projectPropertyName) {
    return property.map { T value ->
      return projectProperty(type, projectPropertyName, value)
    }
  }

  protected <T> T projectProperty(Class<T> type, String projectPropertyName) {
    return projectProperty(type, projectPropertyName, null)
  }

  protected <T> T projectProperty(Class<T> type, String projectPropertyName, T defaultValue) {
    if (project.hasProperty(projectPropertyName)) {
      if (type == Boolean) {
        return Boolean.valueOf(project.property(projectPropertyName).toString()) as T
      }
      return project.property(projectPropertyName).asType(type)
    }
    return defaultValue
  }
}
