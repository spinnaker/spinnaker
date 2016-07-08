/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.handlers;

import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;

import java.util.ArrayList;
import java.util.List;

public class DefaultMigrateSecurityGroupStrategy extends MigrateSecurityGroupStrategy {

  private AmazonClientProvider amazonClientProvider;

  private List<String> infrastructureApplications;

  public AmazonClientProvider getAmazonClientProvider() {
    return amazonClientProvider;
  }

  @Override
  public List<String> getInfrastructureApplications() {
    return infrastructureApplications != null ? infrastructureApplications : new ArrayList<>();
  }

  public DefaultMigrateSecurityGroupStrategy(AmazonClientProvider amazonClientProvider, List<String> infrastructureApplications) {
    this.amazonClientProvider = amazonClientProvider;
    this.infrastructureApplications = infrastructureApplications;
  }
}
