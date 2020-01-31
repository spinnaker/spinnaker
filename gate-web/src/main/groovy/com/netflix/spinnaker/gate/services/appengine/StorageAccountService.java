/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.gate.services.appengine;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.spinnaker.gate.services.commands.HystrixFactory;
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector;
import groovy.transform.CompileStatic;
import java.util.List;
import java.util.concurrent.Callable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@CompileStatic
@Component
@Deprecated
public class StorageAccountService {

  private static final String GROUP = "storageAccount";

  @Autowired private ClouddriverServiceSelector clouddriverServiceSelector;

  private static HystrixCommand<List<String>> listCommand(
      String type, Callable<List<String>> work) {
    return HystrixFactory.newListCommand(GROUP, type, work);
  }

  public List<String> getAppengineStorageAccounts(String selectorKey) {
    return listCommand(
            "appengineStorageAccounts", clouddriverServiceSelector.select()::getStorageAccounts)
        .execute();
  }
}
