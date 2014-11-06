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

import com.codahale.metrics.Timer
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.oort.model.ApplicationProvider
import com.ryantenney.metrics.annotation.Metric
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

@Component
class GoogleApplicationProvider implements ApplicationProvider {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Metric
  Timer applications

  @Metric
  Timer applicationsByName

  GoogleResourceRetriever googleResourceRetriever

  @PostConstruct
  void init() {
    googleResourceRetriever = new GoogleResourceRetriever()
    googleResourceRetriever.init(accountCredentialsProvider)
  }

  @Override
  Set<GoogleApplication> getApplications() {
    applications.time {
      Collections.unmodifiableSet(googleResourceRetriever.getApplicationsMap().values() as Set)
    }
  }

  @Override
  GoogleApplication getApplication(String name) {
    applicationsByName.time {
      googleResourceRetriever.getApplicationsMap()[name]
    }
  }
}
