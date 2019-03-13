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

  @JsonIgnore
  public ApplicationDAO dao

  @JsonIgnore
  ProjectDAO projectDao

  @JsonIgnore
  NotificationDAO notificationDao

  @JsonIgnore
  PipelineDAO pipelineDao

  @JsonIgnore
  PipelineStrategyDAO pipelineStrategyDao

  @JsonIgnore
  Collection<ApplicationValidator> validators

  @JsonIgnore
  Collection<ApplicationEventListener> applicationEventListeners

  void update(Application updatedApplication) {

    updatedApplication.name = this.name
    updatedApplication.createTs = this.createTs
    updatedApplication.description = updatedApplication.description ?: this.description
    updatedApplication.email = updatedApplication.email ?: this.email
    updatedApplication.cloudProviders = updatedApplication.cloudProviders ?: this.cloudProviders
    mergeDetails(updatedApplication, this)
    validate(updatedApplication)

    perform(
        applicationEventListeners.findAll { it.supports(ApplicationEventListener.Type.PRE_UPDATE) },
        applicationEventListeners.findAll { it.supports(ApplicationEventListener.Type.POST_UPDATE) },
        { Application originalApplication, Application modifiedApplication ->
          // onSuccess
          this.dao.update(originalApplication.name.toUpperCase(), modifiedApplication)
          updatedApplication.updateTs = originalApplication.updateTs
          return modifiedApplication
        },
        { Application originalApplication, Application modifiedApplication ->
          // onRollback
          this.dao.update(originalApplication.name.toUpperCase(), originalApplication)
        },
        this,
        updatedApplication
    )
  }

  private static void mergeDetails(Application target, Application source) {
    source.details.each { String key, Object value ->
      if (!target.details.containsKey(key)) {
        target.details[key] = value
      }
    }
  }

  void delete() {
    Application currentApplication = null
    try {
      currentApplication = findByName(this.name)
    } catch (NotFoundException ignored) {
      // do nothing
    }

    if (!currentApplication) {
      log.warn("Application does not exist (name: {}), nothing to delete", value("application", name))
      return
    }

    perform(
        applicationEventListeners.findAll { it.supports(ApplicationEventListener.Type.PRE_DELETE) },
        applicationEventListeners.findAll { it.supports(ApplicationEventListener.Type.POST_DELETE) },
        { Application originalApplication, Application modifiedApplication ->
          // onSuccess
          deleteApplicationComponents(currentApplication.name)
          this.dao.delete(currentApplication.name)
          return null
        },
        { Application originalApplication, Application modifiedApplication ->
          // onRollback
          this.dao.create(currentApplication.name, currentApplication)
          return null
        },
        currentApplication,
        null
    )
  }

  private void deleteApplicationComponents(String application) {
    removeApplicationFromProjects(application)
    deleteApplicationNotifications(application)
    deletePipelines(application)
  }

  private void removeApplicationFromProjects(String application) {
    projectDao.all().findAll {
        it.config.applications.contains(application.toLowerCase())
      }.each {
        log.info("Removing application {} from project {}", application, it.id)
        it.config.applications.remove(application.toLowerCase())
        it.config.clusters.each { cluster ->
          cluster.applications?.remove(application.toLowerCase())
        }
        if (it.config.applications.empty) {
          projectDao.delete(it.id)
        } else {
          projectDao.update(it.id, it)
        }
      }
  }

  private void deleteApplicationNotifications(String application) {
    notificationDao.delete(HierarchicalLevel.APPLICATION, application)
  }

  private void deletePipelines(String application) {
    Collection<Pipeline> pipelinesToDelete = pipelineDao.getPipelinesByApplication(application, true)
    log.info("Deleting pipelines for application {}: {}", application, pipelinesToDelete.findResults { it.id } )
    pipelinesToDelete.each { Pipeline p -> pipelineDao.delete(p.id) }

    Collection<Pipeline> strategiesToDelete = pipelineStrategyDao.getPipelinesByApplication(application)
    log.info("Deleting strategies for application {}: {}", application, strategiesToDelete.findResults { it.id } )
    strategiesToDelete.each { Pipeline p -> pipelineStrategyDao.delete(p.id) }
  }

  Application clear() {
    getPersistedProperties().keySet().each { field ->
      this[field] = null
    }
    this.details = [:]
    return this
  }

  /**
   * Similar to clone but doesn't produce a copy
   */
  Application initialize(Application app) {
    this.clear()
    getPersistedProperties().keySet().each { key ->
      this[key] = app[key]
    }
    return this
  }

  Application save() {
    validate(this)

    try {
      if (findByName(getName())) {
        throw new ApplicationAlreadyExistsException()
      }
    } catch (NotFoundException ignored) {}

    return perform(
        applicationEventListeners.findAll { it.supports(ApplicationEventListener.Type.PRE_CREATE) },
        applicationEventListeners.findAll { it.supports(ApplicationEventListener.Type.POST_CREATE) },
        { Application originalApplication, Application modifiedApplication ->
          // onSuccess
          return dao.create(modifiedApplication.name.toUpperCase(), modifiedApplication)
        },
        { Application originalApplication, Application modifiedApplication ->
          // onRollback
          this.dao.delete(modifiedApplication.name.toUpperCase())
          return null
        },
        null,
        this
    )
  }

  Collection<Application> findAll() {
    try {
      return dao.all() ?: []
    } catch (NotFoundException ignored) {
      return []
    }
  }

  Application findByName(String name) throws NotFoundException {
    if (!name?.trim()) {
      throw new NotFoundException("No application name provided")
    }

    return dao.findByName(name.toUpperCase())
  }

  Set<Application> search(Map<String, String> params) {
    try {
      return dao.search(params) ?: []
    } catch (NotFoundException ignored) {
      return []
    }
  }

  Application withName(String name) {
    this.name = name
    return this
  }

  private void validate(Application application) {
    def errors = new ApplicationValidationErrors(application)
    validators.each {
      it.validate(application, errors)
    }

    if (errors.hasErrors()) {
      throw new ValidationException(errors)
    }
  }

  static Application perform(List<ApplicationEventListener> preApplicationEventListeners,
                             List<ApplicationEventListener> postApplicationEventListeners,
                             @ClosureParams(value = SimpleType, options = [
                                 'com.netflix.spinnaker.front50.model.application.Application',
                                 'com.netflix.spinnaker.front50.model.application.Application'
                             ]) Closure<Application> onSuccess,
                             @ClosureParams(value = SimpleType, options = [
                                 'com.netflix.spinnaker.front50.model.application.Application',
                                 'com.netflix.spinnaker.front50.model.application.Application'
                             ]) Closure<Void> onRollback,
                             Application originalApplication,
                             Application updatedApplication) {
    def copyOfOriginalApplication = copy(originalApplication)

    def invokedEventListeners = []
    try {
      preApplicationEventListeners.each {
        updatedApplication = it.call(copy(copyOfOriginalApplication), copy(updatedApplication)) as Application
        invokedEventListeners << it
      }
      updatedApplication = onSuccess.call(copy(copyOfOriginalApplication), copy(updatedApplication))
      postApplicationEventListeners.each {
        updatedApplication = it.call(copy(copyOfOriginalApplication), copy(updatedApplication))
        invokedEventListeners << it
      }

      return updatedApplication
    } catch (Exception e) {
      invokedEventListeners.each {
        try {
          it.rollback(copy(copyOfOriginalApplication))
        } catch (Exception rollbackException) {
          log.error("Rollback failed (${it.class.simpleName})", rollbackException)
        }
      }
      try {
        onRollback.call(copy(copyOfOriginalApplication), copy(updatedApplication))
      } catch (Exception rollbackException) {
        log.error("Rollback failed (onRollback)", rollbackException)
      }

      log.error("Failed to perform action (name: {})",
        value("application", originalApplication?.name ?: updatedApplication?.name))
      throw e
    }
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

  private static Application copy(Application source) {
    return source ? new Application(source.getPersistedProperties()) : null
  }

  @Canonical
  static class ValidationException extends RuntimeException {
    Errors errors
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
  }

  static class TrafficGuard {
    String account
    String stack
    String detail
    String location
    Boolean enabled = true
  }
}
