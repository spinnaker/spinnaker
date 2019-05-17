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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The "accounts" field was once required but no longer. The value of "accounts" is now inferred
 * from existing infrastructure indexed by Clouddriver, so the value, when present in Front50, just
 * ends up causing confusion.
 */
@Component
public class RemoveApplicationAccountsMigration implements Migration {

  private static final Logger log =
      LoggerFactory.getLogger(RemoveApplicationAccountsMigration.class);

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
    log.info("Starting account field removal migration");
    for (Application a : applicationDAO.all()) {
      if (a.details().containsKey("accounts")) {
        migrate(a);
      }
    }
  }

  private void migrate(Application application) {
    log.info("Removing accounts field from application {}", application.getName());
    application.details().remove("accounts");
    application.dao = applicationDAO;
    application.update(application);
  }
}
