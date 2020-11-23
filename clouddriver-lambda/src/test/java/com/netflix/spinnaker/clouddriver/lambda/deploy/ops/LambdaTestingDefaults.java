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

package com.netflix.spinnaker.clouddriver.lambda.deploy.ops;

import com.amazonaws.services.lambda.model.AliasConfiguration;
import com.amazonaws.services.lambda.model.EventSourceMappingConfiguration;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.LambdaFunction;
import java.util.ArrayList;
import java.util.List;

public interface LambdaTestingDefaults {

  String fName = "app1-stack1-detail1-function1";
  String functionArn = "function1:arn";
  String region = "us-west-2";
  String account = "account-1";
  String version = "1";
  String eventArn = "arn-1";
  String eventUuid = "uuid-1";
  String revisionId = "1";
  String revisionDesc = "Revision Desc";
  String aliasName = "fAlias";
  String aliasArn = "alias-arn-1";
  String aliasDesc = "Alias Description";

  default LambdaFunction getMockedFunctionDefintion() {
    LambdaFunction cachedFunction = new LambdaFunction();
    cachedFunction.setFunctionName(fName);
    cachedFunction.setFunctionArn(functionArn);
    cachedFunction.setRegion(region);
    cachedFunction.setAccount(account);
    cachedFunction.setAliasConfigurations(getMockAliases());
    cachedFunction.setEventSourceMappings(getMockEventSourceList());
    return cachedFunction;
  }

  default List<AliasConfiguration> getMockAliases() {
    AliasConfiguration es = new AliasConfiguration();
    es.setAliasArn(aliasArn);
    es.setDescription(aliasDesc);
    es.setName(aliasName);
    es.setRevisionId(revisionId);
    List<AliasConfiguration> le = new ArrayList();
    le.add(es);
    return le;
  }

  default List<EventSourceMappingConfiguration> getMockEventSourceList() {
    EventSourceMappingConfiguration es = new EventSourceMappingConfiguration();
    es.setUUID(eventUuid);
    es.setEventSourceArn(eventArn);
    List<EventSourceMappingConfiguration> le = new ArrayList();
    le.add(es);
    return le;
  }
}
