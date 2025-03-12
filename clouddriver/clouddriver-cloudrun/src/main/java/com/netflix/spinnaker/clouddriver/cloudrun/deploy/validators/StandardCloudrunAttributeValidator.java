/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.deploy.validators;

import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors;
import com.netflix.spinnaker.credentials.CredentialsRepository;

public class StandardCloudrunAttributeValidator {
  String context;
  ValidationErrors errors;

  public StandardCloudrunAttributeValidator(String context, ValidationErrors errors) {
    this.context = context;
    this.errors = errors;
  }

  public boolean validateCredentials(
      String credentials,
      CredentialsRepository<CloudrunNamedAccountCredentials> credentialsRepository) {
    boolean result = validateNotEmpty(credentials, "account");
    if (result) {
      CloudrunNamedAccountCredentials cloudrunCredentials =
          credentialsRepository.getOne(credentials);
      if (cloudrunCredentials == null) {
        errors.rejectValue("${context}.account", "${context}.account.notFound");
        result = false;
      }
    }
    return result;
  }

  private boolean validateNotEmpty(Object value, String attribute) {
    if (value != "" && value != null) {
      return true;
    } else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.empty");
      return false;
    }
  }
}
