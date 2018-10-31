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

package com.netflix.spinnaker.clouddriver.deploy

import com.netflix.spinnaker.clouddriver.orchestration.VersionedCloudProviderOperation
import com.netflix.spinnaker.clouddriver.security.resources.AccountNameable
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable
import com.netflix.spinnaker.clouddriver.security.resources.ResourcesNameable
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.validation.Errors

public abstract class DescriptionValidator<T> implements VersionedCloudProviderOperation {

  static String getValidatorName(String description) {
    description + "Validator"
  }

  abstract void validate(List priorDescriptions, T description, Errors errors)

  @Autowired(required = false)
  FiatPermissionEvaluator permissionEvaluator

  void authorize(T description, Errors errors) {
    if (!permissionEvaluator) {
      return
    }

    Authentication auth = SecurityContextHolder.context.authentication

    if (description instanceof ApplicationNameable) {
      (description as ApplicationNameable).applications.each { application ->
        if (!permissionEvaluator.hasPermission(auth, application, 'APPLICATION', 'WRITE')) {
          errors.reject("authorization", "Access denied to application ${application}")
        }
      }
    }

    if (description instanceof AccountNameable) {
      AccountNameable asAcct = description as AccountNameable
      if (!permissionEvaluator.hasPermission(auth, asAcct.account, 'ACCOUNT', 'WRITE')) {
        errors.reject("authorization", "Access denied to account ${asAcct.account}")
      }
    }

    if (description instanceof ResourcesNameable) {
      ResourcesNameable asResources = description as ResourcesNameable
      permissionEvaluator.storeWholePermission()
      asResources.resourceApplications.each { String app ->
        if (!permissionEvaluator.hasPermission(auth, app, 'APPLICATION', 'WRITE')) {
          errors.reject("authorization", "Access denied to application ${app}")
        }
      }
    }
  }
}
