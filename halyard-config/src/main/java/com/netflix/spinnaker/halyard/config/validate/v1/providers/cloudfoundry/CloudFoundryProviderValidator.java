/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.halyard.config.validate.v1.providers.cloudfoundry;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.HttpCloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.cloudfoundry.CloudFoundryProvider;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CloudFoundryProviderValidator extends Validator<CloudFoundryProvider> {
    @Autowired
    private String halyardVersion;

    @Override
    public void validate(ConfigProblemSetBuilder problemSetBuilder, CloudFoundryProvider provider) {
        List<CloudFoundryCredentials> credentialsList = new ArrayList<>();

        CloudFoundryAccountValidator cloudFoundryAccountValidator = new CloudFoundryAccountValidator(credentialsList, halyardVersion);
        provider.getAccounts().forEach(cloudFoundryAccount -> cloudFoundryAccountValidator.validate(problemSetBuilder, cloudFoundryAccount));
    }
}
