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

import com.netflix.spinnaker.gate.services.commands.HystrixFactory;
import com.netflix.spinnaker.gate.services.internal.SwabbieService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

@Component
public class CleanupService {
  private static final String GROUP = "cleanup";

  @Autowired(required = false)
  SwabbieService swabbieService;

  public Map optOut(String namespace, String resourceId) {
    return (Map)
        HystrixFactory.newMapCommand(
                GROUP,
                "optOut",
                () -> {
                  try {
                    return swabbieService.optOut(namespace, resourceId, "");
                  } catch (RetrofitError e) {
                    if (e.getResponse().getStatus() == 404) {
                      return Collections.emptyMap();
                    } else {
                      throw e;
                    }
                  }
                })
            .execute();
  }

  public Map get(String namespace, String resourceId) {
    return (Map)
        HystrixFactory.newMapCommand(
                GROUP,
                "get",
                () -> {
                  try {
                    return swabbieService.get(namespace, resourceId);
                  } catch (RetrofitError e) {
                    if (e.getResponse().getStatus() == 404) {
                      return Collections.emptyMap();
                    } else {
                      throw e;
                    }
                  }
                })
            .execute();
  }

  public String restore(String namespace, String resourceId) {
    HystrixFactory.newStringCommand(
            GROUP,
            "restore",
            () -> {
              try {
                swabbieService.restore(namespace, resourceId, "");
              } catch (RetrofitError e) {
                return Integer.toString(e.getResponse().getStatus());
              }
              return "200";
            })
        .execute();
    return "200";
  }

  public List getMarkedList() {
    return (List)
        HystrixFactory.newListCommand(GROUP, "get", () -> swabbieService.getMarkedList(true))
            .execute();
  }

  public List getDeletedList() {
    return (List)
        HystrixFactory.newListCommand(GROUP, "get", () -> swabbieService.getDeletedList())
            .execute();
  }
}
