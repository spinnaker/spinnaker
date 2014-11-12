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

package com.netflix.spinnaker.kato.docker.deploy.validators

import com.netflix.spinnaker.kato.deploy.DescriptionValidator
import com.netflix.spinnaker.kato.docker.deploy.description.DockerDeployDescription
import com.netflix.spinnaker.kato.docker.services.RegistryService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("dockerDeployDescriptionValidator")
class DockerDeployDescriptionValidator extends DescriptionValidator<DockerDeployDescription> {

  @Autowired
  RegistryService registryService

  @Override
  void validate(List priorDescriptions, DockerDeployDescription description, Errors errors) {
    if (!description.application) {
      errors.rejectValue "application", "dockerDeployDescription.application.empty.or.null"
    }
    if (!description.version) {
      errors.rejectValue "version", "dockerDeployDescription.version.empty.or.null"
    }
    try {
      def image = registryService.getImage(description.credentials.credentials, description.application, description.version)
      if (!image) {
        errors.rejectValue "version", "dockerDeployDescription.version.could.not.be.found"
      }
    } catch (e) {
      errors.rejectValue "version", "dockerDeployDescription.version.could.not.be.found"
    }
  }
}
