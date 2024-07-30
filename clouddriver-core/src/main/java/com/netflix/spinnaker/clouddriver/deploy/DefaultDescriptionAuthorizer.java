/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.deploy;

import com.netflix.spinnaker.orchestration.OperationDescription;
import org.springframework.validation.Errors;

public class DefaultDescriptionAuthorizer implements DescriptionAuthorizer {

  private final DescriptionAuthorizerService descriptionAuthorizerService;

  public DefaultDescriptionAuthorizer(DescriptionAuthorizerService descriptionAuthorizerService) {
    this.descriptionAuthorizerService = descriptionAuthorizerService;
  }

  @Override
  public void authorize(OperationDescription description, Errors errors) {
    descriptionAuthorizerService.authorize(description, errors);
  }
}
