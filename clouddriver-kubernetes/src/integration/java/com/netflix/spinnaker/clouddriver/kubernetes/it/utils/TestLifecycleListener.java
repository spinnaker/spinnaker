/*
 * Copyright 2021 Armory
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

package com.netflix.spinnaker.clouddriver.kubernetes.it.utils;

import com.netflix.spinnaker.clouddriver.kubernetes.it.BaseTest;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class TestLifecycleListener
    implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {

  @Override
  public void beforeAll(ExtensionContext context) {
    // initialize "after all test run hook"
    context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL).put("delete_cluster", this);
  }

  @Override
  public void close() {
    BaseTest.kubeCluster.stop();
  }
}
