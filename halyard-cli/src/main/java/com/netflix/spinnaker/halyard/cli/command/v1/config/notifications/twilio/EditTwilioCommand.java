/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.notifications.twilio;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.notifications.AbstractEditNotificationCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Notification;
import com.netflix.spinnaker.halyard.config.model.v1.notifications.TwilioNotification;

/** Interact with the Twilio SMS notification */
@Parameters(separators = "=")
public class EditTwilioCommand extends AbstractEditNotificationCommand<TwilioNotification> {
  protected String getNotificationName() {
    return "twilio";
  }

  @Parameter(names = "--account", description = "Your Twilio account SID.")
  private String account;

  @Parameter(
      names = "--from",
      description = "The phone number from which the SMS will be sent (i.e. +1234-567-8910).")
  private String from;

  @Parameter(names = "--token", password = true, description = "Your Twilio auth token.")
  private String token;

  @Override
  protected Notification editNotification(TwilioNotification notification) {
    notification.setAccount(isSet(account) ? account : notification.getAccount());
    notification.setFrom(isSet(from) ? from : notification.getFrom());
    notification.setToken(isSet(token) ? token : notification.getToken());
    return notification;
  }
}
