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

package com.netflix.spinnaker.echo.scm;

import com.netflix.spinnaker.echo.model.Event;
import java.util.Map;

/**
 * GitWebhookHandler defines an interface processing incoming SCM webhook events.
 * It is responsible for parsing the webhook payload and extracting repoSlug, slug,
 * hash and branch.
 */
public interface GitWebhookHandler {
  /**
   * shouldSendEvent informs the caller if the provided event should be
   * propagated.
   */
  boolean shouldSendEvent(Event event);

  /**
   * handles specifies the source which it is responsible for.
   */
  boolean handles(String source);

  /**
   * handle processes the postedEvent (typically a webhook payload) and should
   * add repoSlug, slug, hash and branch to the provided Event.
   */
  void handle(Event event, Map postedEvent);
}
