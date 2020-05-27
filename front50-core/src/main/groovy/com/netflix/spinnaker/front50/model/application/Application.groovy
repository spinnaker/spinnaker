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


package com.netflix.spinnaker.front50.model.application

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSetter
import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.front50.ServiceAccountsService
import com.netflix.spinnaker.front50.events.ApplicationEventListener
import com.netflix.spinnaker.front50.exception.ApplicationAlreadyExistsException
import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.model.Timestamped
import com.netflix.spinnaker.front50.model.notification.HierarchicalLevel
import com.netflix.spinnaker.front50.model.notification.NotificationDAO
import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import com.netflix.spinnaker.front50.model.pipeline.PipelineStrategyDAO
import com.netflix.spinnaker.front50.model.project.ProjectDAO
import com.netflix.spinnaker.front50.validator.ApplicationValidationErrors
import com.netflix.spinnaker.front50.validator.ApplicationValidator
import groovy.transform.Canonical
import groovy.transform.ToString
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import groovy.util.logging.Slf4j
import org.springframework.validation.Errors

import static net.logstash.logback.argument.StructuredArguments.value

@ToString
@Slf4j
class Application implements Timestamped {
  String name
  String description
  String email
  String updateTs
  String createTs
  String lastModifiedBy
  Object cloudProviders // might be persisted as a List or a String

  private Map<String, Object> details = new HashMap<String, Object>()

  String getCloudProviders() {
    // Orca expects a String
    return cloudProviders instanceof List ? cloudProviders.join(',') : cloudProviders
  }

  String getName() {
    // there is an expectation that application names are uppercased (historical)
    return name?.toUpperCase()?.trim()
  }

  List<TrafficGuard> getTrafficGuards() {
    (List<TrafficGuard>) details.trafficGuards ?: []
  }

  void setTrafficGuards(List<TrafficGuard> trafficGuards) {
    set("trafficGuards", trafficGuards)
  }

  @JsonAnyGetter
  public Map<String,Object> details() {
    return details
  }

  @JsonAnySetter
  public void set(String name, Object value) {
    details.put(name, value)
  }

  @JsonIgnore
  public Map<String, Object> getPersistedProperties() {
    [
        name: this.name,
        description: this.description,
        email: this.email,
        updateTs: this.updateTs,
        createTs: this.createTs,
        details: this.details,
        cloudProviders: this.cloudProviders,
    ]
  }

  @Override
  @JsonIgnore()
  String getId() {
    return name.toLowerCase()
  }

  @Override
  @JsonIgnore
  Long getLastModified() {
    return updateTs ? Long.valueOf(updateTs) : null
  }

  @Override
  void setLastModified(Long lastModified) {
    this.updateTs = lastModified.toString()
  }

  @Override
  String toString() {
    return "Application{" +
      "name='" + name + '\'' +
      ", description='" + description + '\'' +
      ", email='" + email + '\'' +
      ", updateTs='" + updateTs + '\'' +
      ", createTs='" + createTs + '\'' +
      ", lastModifiedBy='" + lastModifiedBy + '\'' +
      ", cloudProviders=" + cloudProviders +
      '}';
  }

  static class Permission implements Timestamped {
    String name
    Long lastModified
    String lastModifiedBy
    Permissions permissions = Permissions.EMPTY

    @Override
    @JsonIgnore
    String getId() {
      return name.toLowerCase()
    }

    @JsonSetter
    void setRequiredGroupMembership(List<String> requiredGroupMembership) {
      log.warn("Required group membership settings detected in application {} " +
        "Please update to `permissions` format.", value("application", name))

      if (!permissions.isRestricted()) { // Do not overwrite permissions if it contains values
        Permissions.Builder b = new Permissions.Builder()
        requiredGroupMembership.each {
          b.add(Authorization.READ, it.trim().toLowerCase())
          b.add(Authorization.WRITE, it.trim().toLowerCase())
        }
        permissions = b.build()
      }
    }

    Permission copy() {
      // It's OK to "copy" permissions without actually copying since the object is immutable.
      return new Permission(
        name: name,
        lastModified: lastModified,
        lastModifiedBy: lastModifiedBy,
        permissions: permissions
      )
    }
  }

  static class TrafficGuard {
    String account
    String stack
    String detail
    String location
    Boolean enabled = true
  }
}
