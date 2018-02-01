/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.model

class PubSubTriggerSpec extends AbstractTriggerSpec<PubSubTrigger> {
  @Override
  protected Class<PubSubTrigger> getType() {
    PubSubTrigger
  }

  @Override
  protected String getTriggerJson() {
    '''
{
  "attributeConstraints": {
    "eventType": "OBJECT_FINALIZE"
  },
  "enabled": true,
  "expectedArtifactIds": [
    "bcebbb6b-8262-4efb-99c5-7bed4a6f4d15"
  ],
  "payloadConstraints": {
    "key": "value"
  },
  "pubsubSystem": "google",
  "subscriptionName": "subscription",
  "type": "pubsub"
}'''
  }
}
