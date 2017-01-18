/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.deploy.deployment.v1;

import static com.netflix.spinnaker.halyard.deploy.component.v1.ComponentType.CLOUDDRIVER;

import com.netflix.spinnaker.halyard.deploy.provider.v1.Provider;

/**
 * A Deployment is a running Spinnaker installation.
 *
 * During instantiation it's given an instance of a Provider, which it uses to create an instance
 * of Clouddriver. The Provider ensures Clouddriver is reachable by this Deployment object, and
 * then proceeds to use Clouddriver to deploy the remaining Spinnaker services.
 */
public class Deployment {
  public Deployment(Provider provider) {
    provider.bootstrapClouddriver();
    provider.connectTo(CLOUDDRIVER);
  }
}
