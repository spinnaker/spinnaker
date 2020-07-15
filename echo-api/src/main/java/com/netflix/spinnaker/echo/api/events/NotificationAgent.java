/*
 * Copyright 2020 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.echo.api.events;

import com.netflix.spinnaker.kork.annotations.Alpha;
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint;
import java.util.Map;
import javax.annotation.Nonnull;

/** A NotificationAgent handles user-configured pipeline notifications. */
@Alpha
public interface NotificationAgent extends SpinnakerExtensionPoint {
  /** The notification's type (e.g., Slack, SMS, email). */
  @Nonnull
  String getNotificationType();

  /**
   * @param notificationConfig - User-defined configuration. Arbitrary key-value pairs can be passed
   *     to the agent here (e.g., a Slack channel name).
   * @param application - The name of the application where the pipeline was run.
   * @param event - a Spinnaker {@link Event}.
   * @param status - The state of the referenced resource (e.g., failed, complete, etc.).
   */
  void sendNotifications(
      @Nonnull Map<String, Object> notificationConfig,
      @Nonnull String application,
      @Nonnull Event event,
      @Nonnull String status);
}
