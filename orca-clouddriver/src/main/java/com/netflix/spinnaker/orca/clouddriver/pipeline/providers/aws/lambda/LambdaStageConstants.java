/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.lambda;

import java.util.List;

public class LambdaStageConstants {

  public static final String lambaCreatedKey = "lambdaCreated";
  public static final String lambaCodeUpdatedKey = "lambdaCodeUpdated";
  public static final String lambaConfigurationUpdatedKey = "lambdaConfigUpdated";
  public static final String lambaVersionPublishedKey = "lambdaVersionPublished";
  public static final String lambaAliasesUpdatedKey = "lambdaAliasesUpdated";

  public static final String createdUrlKey = "createdUrl";
  public static final String updateCodeUrlKey = "updateCodeUrl";
  public static final String updateConfigUrlKey = "updateConfigUrl";
  public static final String updateEventUrlKey = "updateEventUrl";
  public static final String publishVersionUrlKey = "publishVersionUrl";
  public static final String putConcurrencyUrlKey = "lambdaPutConcurrencyUrl";
  public static final String deleteConcurrencyUrlKey = "lambdaDeleteConcurrencyUrl";
  public static final String eventTaskKey = "eventConfigUrlList";
  public static final String aliasTaskKey = "updateAliasesUrlList";
  public static final String lambdaObjectKey = "lambdaObject";
  public static final String originalRevisionIdKey = "originalRevisionId";
  public static final String revisionIdKey = "revisionId";
  public static final String allRevisionsKey = "allRevisions";
  public static final String versionIdKey = "versionId";
  public static final String newRevisionIdKey = "newRevisionId";
  public static final String functionARNKey = "functionARN";
  public static final String resourceIdKey = "resourceId";
  public static final String functionNameKey = "functionName";
  public static final String urlKey = "url";

  public static List<String> allUrlKeys =
      List.of(
          createdUrlKey,
          updateCodeUrlKey,
          updateConfigUrlKey,
          updateEventUrlKey,
          publishVersionUrlKey,
          putConcurrencyUrlKey,
          deleteConcurrencyUrlKey);
}
