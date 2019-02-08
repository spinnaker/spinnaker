/*
 * Copyright 2018 Pivotal, Inc.
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

package com.netflix.spinnaker.halyard.config.validate.v1.providers.cloudfoundry;

import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.cloudfoundry.CloudFoundryAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
public class CloudFoundryAccountValidator extends Validator<CloudFoundryAccount> {
    final private List<CloudFoundryCredentials> credentialsList;
    final private String halyardVersion;

    @Override
    public void validate(ConfigProblemSetBuilder problemSetBuilder, CloudFoundryAccount cloudFoundryAccount) {
        String accountName = cloudFoundryAccount.getName();

        DaemonTaskHandler.message("Validating " + accountName + " with " + CloudFoundryAccountValidator.class.getSimpleName());

        String environment = cloudFoundryAccount.getEnvironment();
        String api = cloudFoundryAccount.getApi();
        String appsManagerURI = cloudFoundryAccount.getAppsManagerURI();
        String metricsURI = cloudFoundryAccount.getMetricsURI();
        String password = cloudFoundryAccount.getPassword();
        String user = cloudFoundryAccount.getUser();

        if (StringUtils.isEmpty(environment)) {
            problemSetBuilder.addProblem(Problem.Severity.ERROR, "You must provide an environment name");
        }

        if (StringUtils.isEmpty(user) || StringUtils.isEmpty(password)) {
            problemSetBuilder.addProblem(Problem.Severity.ERROR, "You must provide a user and a password");
        }

        if (StringUtils.isEmpty(api)) {
            problemSetBuilder.addProblem(Problem.Severity.ERROR, "You must provide a CF api endpoint");
        }

        if (StringUtils.isEmpty(appsManagerURI)) {
            problemSetBuilder.addProblem(Problem.Severity.WARNING,
                    "To be able to link server groups to CF Appsmanager a URI is required: " + accountName);
        }

        if (StringUtils.isEmpty(metricsURI)) {
            problemSetBuilder.addProblem(Problem.Severity.WARNING,
                    "To be able to link server groups to CF Metrics a URI is required: " + accountName);
        }

        try {
            CloudFoundryCredentials cloudFoundryCredentials = new CloudFoundryCredentials(
                    cloudFoundryAccount.getName(),
                    appsManagerURI,
                    metricsURI,
                    api,
                    user,
                    password,
                    environment
            );
            credentialsList.add(cloudFoundryCredentials);
            Collection<Map<String, String>> regions = cloudFoundryCredentials.getRegions();
            if (regions.isEmpty()) {
                throw new Exception("No spaces returned for account: " + accountName);
            }
        } catch (Exception e) {
            problemSetBuilder.addProblem(Problem.Severity.ERROR, "Failed to establish a connection for account '"
                    + accountName + "': " + e.getMessage() + ".");
        }
    }
}
