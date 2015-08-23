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


package com.netflix.spinnaker.front50.events

import com.netflix.spinnaker.front50.model.application.Application
import org.springframework.stereotype.Component

@Component
class UnmodifiableAttributesApplicationEventListener implements ApplicationEventListener {
  @Override
  boolean supports(ApplicationEventListener.Type type) {
    return type == ApplicationEventListener.Type.PRE_UPDATE
  }

  @Override
  Application call(Application currentApplication, Application updatedApplication) {
    ["name", "updateTs", "createTs"].each {
      // remove attributes that should not be externally modifiable
      updatedApplication."${it}" = currentApplication."${it}"
    }
    return updatedApplication
  }

  @Override
  void rollback(Application originalApplication) {
    // do nothing
  }
}
