/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.oort.cf.model

import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Timer
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.kato.cf.security.CloudFoundryAccountCredentials
import com.netflix.spinnaker.oort.model.InstanceProvider
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct
import java.util.concurrent.Callable

@Component
@CompileStatic
class CloudFoundryApplicationInstanceProvider implements InstanceProvider<CloudFoundryApplicationInstance> {

  @Autowired
  Registry registry

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  String platform = 'cf'

  @Autowired
  CloudFoundryResourceRetriever cloudFoundryResourceRetriever

  Timer instancesByAccountRegionId

  @PostConstruct
  void init() {
    String[] tags = ['className', this.class.simpleName]
    instancesByAccountRegionId = registry.timer('instancesByAccountRegionId', tags)
  }

  @Override
  CloudFoundryApplicationInstance getInstance(String account, String region, String id) {
    instancesByAccountRegionId.record({
      cloudFoundryResourceRetriever.instancesByAccountAndId[account][id]
    } as Callable<CloudFoundryApplicationInstance>)
  }

  @Override
  String getConsoleOutput(String account, String region, String id) {
    def accountCredentials = accountCredentialsProvider.getCredentials(account)

    if (!(accountCredentials?.credentials instanceof CloudFoundryAccountCredentials)) {
      throw new IllegalArgumentException("Invalid credentials: ${account}:${region}")
    }

    // TODO: Figure out how to talk to loggregator?

    return null
  }

}
