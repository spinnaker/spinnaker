/*
 * Copyright 2016 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cf.deploy.validators

import com.netflix.spinnaker.clouddriver.cf.CloudFoundryOperation
import com.netflix.spinnaker.clouddriver.cf.deploy.description.CloudFoundryDeployDescription
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@CloudFoundryOperation(AtomicOperations.CLONE_SERVER_GROUP)
@Component("cloneCloudFoundryServerGroupDescriptionValidator")
class CloneCloudFoundryServerGroupDescriptionValidator extends DescriptionValidator<CloudFoundryDeployDescription> {

	@Autowired
	AccountCredentialsProvider accountCredentialsProvider

	@Override
	void validate(List priorDescriptions, CloudFoundryDeployDescription description, Errors errors) {
		def helper = new StandardCfAttributeValidator("cloneCloudFoundryServerGroupDescription", errors)

		helper.validateCredentials(description.credentialAccount, accountCredentialsProvider)

		helper.validateNotEmpty(description?.credentials?.api, "cloneCloudFoundryServerGroupDescription.credentials.api")
		helper.validateNotEmpty(description?.credentials?.org, "cloneCloudFoundryServerGroupDescription.credentials.org")
		helper.validateNotEmpty(description?.credentials?.space, "cloneCloudFoundryServerGroupDescription.credentials.space")

		helper.validateNotEmpty(description?.application, "cloneCloudFoundryServerGroupDescription.application")

		if (description?.targetSize != null) {
			helper.validatePositiveInt(description.targetSize, "cloneCloudFoundryServerGroupDescription.targetSize")
		}

		if (description?.repository?.contains('{{') && description?.repository?.contains('}}') && !description?.trigger) {
			errors.rejectValue(
					'cloneCloudFoundryServerGroupDescription.repository',
					'cloneCloudFoundryServerGroupDescription.repository.templateWithNoParameters')
		}
	}
}
