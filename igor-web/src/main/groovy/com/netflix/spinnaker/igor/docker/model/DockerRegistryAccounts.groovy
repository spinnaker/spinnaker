/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.igor.docker.model

import com.netflix.spinnaker.igor.docker.service.ClouddriverService
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import retrofit.RetrofitError

@Slf4j
class DockerRegistryAccounts {
    @Autowired
    ClouddriverService service

    List<Map> accounts

    DockerRegistryAccounts() {
        this.accounts = []
    }

    void updateAccounts() {
        try {
            AuthenticatedRequest.allowAnonymous {
                this.accounts = service.allAccounts.findAll { it.cloudProvider == 'dockerRegistry' }*.name.collect {
                    service.getAccountDetails(it)
                }
            }
        } catch (RetrofitError e) {
            log.error "Failed to get list of docker accounts", e
        }
    }
}
