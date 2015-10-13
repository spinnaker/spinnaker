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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.front50.events.ApplicationEventListener
import com.netflix.spinnaker.front50.exception.ApplicationAlreadyExistsException
import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.validator.ApplicationValidationErrors
import com.netflix.spinnaker.front50.validator.ApplicationValidator
import groovy.transform.Canonical
import groovy.transform.ToString
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import groovy.util.logging.Slf4j
import org.springframework.validation.Errors

import java.lang.reflect.Field
import java.lang.reflect.Modifier

@ToString
@Slf4j
class Application {
  String name
  String description
  String email
  String owner
  String type
  String group
  String monitorBucketType
  String pdApiKey
  String updateTs
  String createTs
  String tags
  String regions
  String accounts
  String repoProjectKey
  String repoSlug
  String repoType
  String cloudProviders
  String platformHealthOnly
  String platformHealthOnlyShowOverride

  @JsonIgnore
  ApplicationDAO dao

  @JsonIgnore
  Collection<ApplicationValidator> validators

  @JsonIgnore
  Collection<ApplicationEventListener> applicationEventListeners

  Application() {} //forces Groovy to add LinkedHashMap constructor

  @JsonCreator
  Application(@JsonProperty("name") String name,
              @JsonProperty("description") String description,
              @JsonProperty("email") String email,
              @JsonProperty("owner") String owner,
              @JsonProperty("type") String type,
              @JsonProperty("group") String group,
              @JsonProperty("monitorBucketType") String monitorBucketType,
              @JsonProperty("pdApiKey") String pdApiKey,
              @JsonProperty("regions") String regions,
              @JsonProperty("tags") String tags,
              @JsonProperty("accounts") String accounts,
              @JsonProperty("createTs") String createdAt,
              @JsonProperty("updateTs") String updatedAt,
              @JsonProperty("repoProjectKey") String repoProjectKey,
              @JsonProperty("repoSlug") String repoSlug,
              @JsonProperty("repoType") String repoType,
              @JsonProperty("cloudProviders") String cloudProviders,
              @JsonProperty("platformHealthOnly") String platformHealthOnly,
              @JsonProperty("platformHealthOnlyShowOverride") String platformHealthOnlyShowOverride

  ) {
    this.group = group
    this.monitorBucketType = monitorBucketType
    this.pdApiKey = pdApiKey
    this.name = name
    this.description = description
    this.email = email
    this.owner = owner
    this.type = type
    this.regions = regions
    this.tags = tags
    this.accounts = accounts
    this.createTs = createdAt
    this.updateTs = updatedAt
    this.repoProjectKey = repoProjectKey
    this.repoSlug = repoSlug
    this.repoType = repoType
    this.cloudProviders = cloudProviders
    this.platformHealthOnly = platformHealthOnly
    this.platformHealthOnlyShowOverride = platformHealthOnlyShowOverride
  }

  void update(Application updatedApplication) {
    def newAttributes = updatedApplication.allSetColumnProperties()
    validate(new Application(allColumnProperties() << newAttributes))

    updatedApplication = perform(
        applicationEventListeners.findAll { it.supports(ApplicationEventListener.Type.PRE_UPDATE) },
        applicationEventListeners.findAll { it.supports(ApplicationEventListener.Type.POST_UPDATE) },
        { Application originalApplication, Application modifiedApplication ->
          // onSuccess
          this.dao.update(originalApplication.name.toUpperCase(), modifiedApplication.allSetColumnProperties())
          return modifiedApplication
        },
        { Application originalApplication, Application modifiedApplication ->
          // onRollback
          this.dao.update(originalApplication.name.toUpperCase(), originalApplication.allColumnProperties())
        },
        this,
        updatedApplication
    )

    updatedApplication.allSetColumnProperties().each { String key, String value ->
      // apply updates locally (in addition to DAO persistence)
      this[key] = value
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
      log.warn("Application does not exist (name: ${name}), nothing to delete")
      return
    }

    perform(
        applicationEventListeners.findAll { it.supports(ApplicationEventListener.Type.PRE_DELETE) },
        applicationEventListeners.findAll { it.supports(ApplicationEventListener.Type.POST_DELETE) },
        { Application originalApplication, Application modifiedApplication ->
          // onSuccess
          this.dao.delete(currentApplication.name)
          return null
        },
        { Application originalApplication, Application modifiedApplication ->
          // onRollback
          this.dao.create(currentApplication.name, currentApplication.allColumnProperties())
          return null
        },
        currentApplication,
        null
    )
  }

  Application clear() {
    Application.declaredFields.each { field ->
      if (isColumnProperty(field)) {
        this."$field.name" = null
      }
    }
    return this
  }

  /**
   * Similar to clone but doesn't produce a copy
   */
  Application initialize(Application app) {
    this.clear()
    Application.declaredFields.each { field ->
      if (isColumnProperty(field)) {
        def value = app."$field.name"
        if (value) {
          this."$field.name" = value
        }
      }
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
          return dao.create(modifiedApplication.name.toUpperCase(), modifiedApplication.allSetColumnProperties())
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

  Map<String, String> allSetColumnProperties() {
    return allColumnProperties(true)
  }

  Map<String, String> allColumnProperties(boolean onlySet = false) {
    Application.declaredFields.toList().findResults {
      if (isColumnProperty(it)) {
        def value = this."$it.name"
        if (onlySet) {
          // consider empty strings to be 'set' values
          return (value != null) ? [it.name, value] : null
        }
        return [it.name, value]
      }
      null
    }.collectEntries()
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
      onSuccess.call(copy(copyOfOriginalApplication), copy(updatedApplication))
      postApplicationEventListeners.each {
        it.call(copy(copyOfOriginalApplication), copy(updatedApplication))
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

      log.error("Failed to perform action (name: ${originalApplication.name ?: updatedApplication.name})")
      throw e
    }
  }

  private static Application copy(Application source) {
    return source ? new Application(source.allColumnProperties()) : null
  }

  private static boolean isColumnProperty(Field field) {
    (field.modifiers == Modifier.PRIVATE) && (field.genericType == String.class)
  }

  @Canonical
  static class ValidationException extends RuntimeException {
    Errors errors
  }
}