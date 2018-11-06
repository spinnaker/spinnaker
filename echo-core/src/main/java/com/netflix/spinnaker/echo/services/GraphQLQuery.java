/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.echo.services;

import javax.annotation.Nullable;

import static java.lang.String.format;

public class GraphQLQuery {

  /**
   * Oh yeah. This is the realness. Will need to write a query builder.
   */
  public static GraphQLQuery pipelines(@Nullable String havingTriggerType) {
    if (havingTriggerType != null) {
      havingTriggerType = format("(havingTriggerType: \"%s\")", havingTriggerType);
    } else {
      havingTriggerType = "";
    }

    return new GraphQLQuery(format("query {\n" +
      "  pipelines%s {\n" +
      "    id\n" +
      "    name\n" +
      "    application\n" +
      "    triggers {\n" +
      "      ... on CronTrigger {\n" +
      "        id\n" +
      "        enabled\n" +
      "        cronExpression\n" +
      "      }\n" +
      "    }\n" +
      "  }\n" +
      "}\n", havingTriggerType));
  }

  public String query;

  GraphQLQuery(String query) {
    this.query = query;
  }
}
