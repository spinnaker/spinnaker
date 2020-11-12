/*
 * Copyright 2020 Armory, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.validators;

import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.AbstractCloudFoundryDescription;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors;
import java.util.List;

public abstract class AbstractCloudFoundryDescriptionValidator
    extends DescriptionValidator<AbstractCloudFoundryDescription> {

  @Override
  public void validate(
      List<AbstractCloudFoundryDescription> priorDescriptions,
      AbstractCloudFoundryDescription description,
      ValidationErrors errors) {
    StandardCloudFoundryAttributeValidator helper =
        new StandardCloudFoundryAttributeValidator(description.getClass().getSimpleName(), errors);
    helper.validateRegions(description.getRegion(), description.getCredentials());
  }
}
