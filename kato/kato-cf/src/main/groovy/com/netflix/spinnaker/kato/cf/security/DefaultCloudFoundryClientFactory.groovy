/*
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.spinnaker.kato.cf.security

import org.cloudfoundry.client.lib.CloudFoundryClient
import org.cloudfoundry.client.lib.CloudFoundryOperations
/**
 * A factory for creating {@link CloudFoundryClient} objects. This allows delaying the creation until ALL
 * the details are gathered (some come via {@link com.netflix.spinnaker.kato.cf.deploy.description.CloudFoundryDeployDescription}
 *
 *
 */
class DefaultCloudFoundryClientFactory implements CloudFoundryClientFactory {

  CloudFoundryOperations createCloudFoundryClient(CloudFoundryAccountCredentials credentials, boolean trustSelfSignedCerts) {
    new CloudFoundryClient(
        credentials.credentials,
        credentials.api.toURL(),
        credentials.org,
        credentials.space,
        trustSelfSignedCerts)
  }

}
