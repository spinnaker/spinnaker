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

package com.netflix.spinnaker.halyard.cli.command.v1.config.pubsubs.google;

public class GooglePubsubCommandProperties {
  public static final String TEMPLATE_PATH_DESCRIPTION =
      "A path to a jinja template that specifies how artifacts from this pubsub system are interpreted and transformed into Spinnaker artifacts. See spinnaker.io/reference/artifacts for more information.";

  public static final String PROJECT_DESCRIPTION =
      "The name of the GCP project your subscription lives in.";

  public static final String SUBSCRIPTION_NAME_DESCRIPTION =
      "The name of the subscription to listen to. This identifier does not include the name of the project, and must already be configured for Spinnaker to work.";

  public static final String TOPIC_NAME_DESCRIPTION =
      "The name of the topic to publish to. This identifier does not include the name of the project, and must already be configured for Spinnaker to work.";

  public static final String ACK_DEADLINE_SECONDS_DESCRIPTION =
      "Time in seconds before an outstanding message is considered unacknowledged and is re-sent.\n"
          + "Configurable in your Google Cloud Pubsub subscription. See the docs here: https://cloud.google.com/pubsub/docs/subscriber";

  public static final String MESSAGE_FORMAT_DESCRIPTION =
      "One of 'GCB', 'GCS', 'GCR', or 'CUSTOM'. This can be used to help Spinnaker translate the contents of the\n"
          + "Pub/Sub message into Spinnaker artifacts.";
}
