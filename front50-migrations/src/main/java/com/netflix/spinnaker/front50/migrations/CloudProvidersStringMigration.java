/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.front50.migrations;

import com.netflix.spinnaker.front50.model.application.Application;
import com.netflix.spinnaker.front50.model.application.ApplicationDAO;
import java.time.Clock;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CloudProvidersStringMigration implements Migration {
  private static final Logger log = LoggerFactory.getLogger(CloudProvidersStringMigration.class);

  // Only valid until June 1st, 2020
  private static final Date VALID_UNTIL = new GregorianCalendar(2020, 6, 1).getTime();

  @Autowired private ApplicationDAO applicationDAO;

  private Clock clock = Clock.systemDefaultZone();

  @Override
  public boolean isValid() {
    return clock.instant().toEpochMilli() < VALID_UNTIL.getTime();
  }

  @Override
  public void run() {
    log.info("Starting cloud provider string migration");
    for (Application a : applicationDAO.all()) {
      if (a.details().get("cloudProviders") instanceof List) {
        migrate(a);
      }
    }
  }

  private void migrate(Application application) {
    log.info(
        "Converting cloudProviders ({}) for application {} from a List to a String for {}",
        application.details().get("cloudProviders").toString(),
        application.getName());
    application.set(
        "cloudProviders",
        String.join(",", (List<String>) application.details().get("cloudProviders")));
    application.dao = applicationDAO;
    application.update(application);
  }
}
