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
 *
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.pubsubs.subscription;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.pubsubs.AbstractPubsubCommand;

/** An abstract definition for commands that accept SUBSCRIPTION as a main parameter */
@Parameters(separators = "=")
public abstract class AbstractHasSubscriptionCommand extends AbstractPubsubCommand {
  @Parameter(description = "The name of the subscription to operate on.")
  String subscription;

  @Override
  public String getMainParameter() {
    return "subscription";
  }

  public String getSubscriptionName(String defaultName) {
    try {
      return getSubscriptionName();
    } catch (IllegalArgumentException e) {
      return defaultName;
    }
  }

  public String getSubscriptionName() {
    if (subscription == null) {
      throw new IllegalArgumentException("No subscription name supplied");
    }
    return subscription;
  }
}
