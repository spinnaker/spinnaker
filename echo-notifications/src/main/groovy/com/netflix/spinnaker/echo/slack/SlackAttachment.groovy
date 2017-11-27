/*
 * Copyright 2015 Netflix, Inc.
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


package com.netflix.spinnaker.echo.slack

import groovy.transform.Canonical

/**
 * This is formatted automatically to JSON when being sent to Slack as an attachment message.
 */
@Canonical
class SlackAttachment {

  String title
  String text
  String color
  String fallback

  // From https://github.com/spinnaker
  String footer_icon = "https://avatars0.githubusercontent.com/u/7634182?s=200&v=4"
  String footer = "Spinnaker"
  // Specify which fields Slack should format using markdown
  List<String> mrkdwn_in = ["text"]
  // The pretty date will appear in the footer
  long ts = System.currentTimeMillis() / 1000

  public SlackAttachment(String title, String text, String color = '#cccccc') {
    this.title = title
    this.text = this.fallback = text
    this.color = color
  }
}
