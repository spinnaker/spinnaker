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

import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.DeployCloudrunDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("deployCloudrunAtomicOperationDescription")
public class DeployCloudrunDescriptionValidator
    extends DescriptionValidator<DeployCloudrunDescription> {
  @Autowired CredentialsRepository<CloudrunNamedAccountCredentials> credentialsRepository;

  @Override
  public void validate(
      List priorDescriptions, DeployCloudrunDescription description, ValidationErrors errors) {
    StandardCloudrunAttributeValidator helper =
        new StandardCloudrunAttributeValidator("deployCloudrunAtomicOperationDescription", errors);

    if (!helper.validateCredentials(description.getAccountName(), credentialsRepository)) {
      return;
    }
  }
}
