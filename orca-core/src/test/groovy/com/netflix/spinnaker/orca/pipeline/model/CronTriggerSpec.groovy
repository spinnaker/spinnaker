/*
 * Copyright 2017 Netflix, Inc.
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

class CronTriggerSpec extends AbstractTriggerSpec<CronTrigger> {
  @Override
  protected Class<CronTrigger> getType() {
    return CronTrigger
  }

  @Override
  protected String getTriggerJson() {
    return '''
{
  "cronExpression":"0 0/12 * 1/1 * ? *",
  "id":"197f94fc-466f-4aa5-9c95-65666e28e8fb",
  "type":"cron",
  "parameters":{},
  "user":"[anonymous]",
  "enabled":true
}'''
  }
}
