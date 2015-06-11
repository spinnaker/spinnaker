/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.oort.gce.model

import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spectator.api.Timer
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.oort.model.ApplicationProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct
import java.util.concurrent.Callable

@Component
class GoogleApplicationProvider implements ApplicationProvider {

  @Autowired
  ExtendedRegistry extendedRegistry

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  GoogleResourceRetriever googleResourceRetriever

  Timer applications
  Timer applicationByName

  @PostConstruct
  void init() {
    String[] tags = ['className', this.class.simpleName]
    applications = extendedRegistry.timer('applications', tags)
    applicationByName = extendedRegistry.timer('applicationByName', tags)
  }

  @Override
  Set<GoogleApplication> getApplications() {
    applications.record({
      Collections.unmodifiableSet(googleResourceRetriever.getApplicationsMap().values() as Set)
    } as Callable<Set<GoogleApplication>>)
  }

  @Override
  GoogleApplication getApplication(String name) {
    applicationByName.record({
      googleResourceRetriever.getApplicationsMap()[name]
    } as Callable<GoogleApplication>)
  }
}
