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

package com.netflix.spinnaker.gate.services;

import com.netflix.spinnaker.gate.services.internal.SwabbieService;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CleanupService {

  @Autowired(required = false)
  SwabbieService swabbieService;

  public Map optOut(String namespace, String resourceId) {
    try {
      return Retrofit2SyncCall.execute(swabbieService.optOut(namespace, resourceId, ""));
    } catch (SpinnakerHttpException e) {
      if (e.getResponseCode() == 404) {
        return Collections.emptyMap();
      }
      throw e;
    }
  }

  public Map get(String namespace, String resourceId) {
    try {
      return Retrofit2SyncCall.execute(swabbieService.get(namespace, resourceId));
    } catch (SpinnakerHttpException e) {
      if (e.getResponseCode() == 404) {
        return Collections.emptyMap();
      }
      throw e;
    }
  }

  public List getMarkedList() {
    return Retrofit2SyncCall.execute(swabbieService.getMarkedList(true));
  }

  public List getDeletedList() {
    return Retrofit2SyncCall.execute(swabbieService.getDeletedList());
  }
}
