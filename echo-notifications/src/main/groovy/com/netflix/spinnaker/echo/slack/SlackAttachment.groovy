/*
 * Copyright 2020 Netflix, Inc.
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


package com.netflix.spinnaker.echo.slack

import com.netflix.spinnaker.echo.api.Notification
import com.netflix.spinnaker.echo.api.Notification.ButtonAction
import com.netflix.spinnaker.echo.api.Notification.InteractiveActions
import groovy.transform.Canonical

import java.util.stream.Collectors

/**
 * This is formatted automatically to JSON when being sent to Slack as an attachment message.
 */
@Canonical
class SlackAttachment {
  String title
  String text
  String color
  String fallback
  String callback_id
  List<SlackAction> actions = []

  // From https://github.com/spinnaker
  String footer_icon = "https://avatars0.githubusercontent.com/u/7634182?s=200&v=4"
  String footer = "Spinnaker"
  // Specify which fields Slack should format using markdown
  List<String> mrkdwn_in = ["text"]
  // The pretty date will appear in the footer
  long ts = System.currentTimeMillis() / 1000

  SlackAttachment(String title, String text, String color = '#cccccc') {
    this.title = title
    this.text = this.fallback = text
    this.color = color
    this.actions = actions
  }

  SlackAttachment(String title, String text, InteractiveActions interactiveActions) {
    this(title, text)

    if (interactiveActions != null) {
      this.actions = extractSlackActions(interactiveActions)
      this.callback_id = interactiveActions.callbackServiceId + ":" + interactiveActions.callbackMessageId
      this.color = interactiveActions.color
    }
  }

  private List<SlackAction> extractSlackActions(InteractiveActions interactiveActions) {
    List<SlackAction> slackActions = interactiveActions.actions.stream()
      .filter() { it instanceof ButtonAction }
      .map { SlackButtonAction.copy(it) }
      .collect(Collectors.toList())
    slackActions
  }

  static class SlackAction {
    final String type
    final String name
    final String value

    SlackAction(String type, String name, String value) {
      this.type = type
      this.name = name
      this.value = value
    }
  }

  static class SlackButtonAction extends SlackAction {
    final String text

    SlackButtonAction(String name, String text, String value) {
      super("button", name, value)
      if (name == null || text == null || value == null) {
        throw new IllegalArgumentException("name, text and value are required")
      }
      this.text = text
    }

    static SlackButtonAction copy(ButtonAction action) {
      if (action == null) {
        throw new IllegalArgumentException("Action cannot be null")
      }

      new SlackButtonAction(action.name, action.label, action.value)
    }
  }
}

