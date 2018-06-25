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

package com.netflix.spinnaker.echo.api

class Notification {
  Type notificationType
  Collection<String> to
  Collection<String> cc
  String templateGroup
  Severity severity

  Source source
  Map<String, Object> additionalContext = [:]

  static class Source {
    String executionType
    String executionId
    String application
    String user
  }

  static enum Type {
    HIPCHAT,
    EMAIL,
    SMS,
    SLACK,
    PAGER_DUTY,
    JIRA,
    BEARYCHAT,
    GOOGLECHAT
  }

  static enum Severity {
    NORMAL,
    HIGH
  }
}
