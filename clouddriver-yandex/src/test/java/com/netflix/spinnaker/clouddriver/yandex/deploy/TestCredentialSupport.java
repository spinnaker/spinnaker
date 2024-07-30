/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.deploy;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.orchestration.OperationDescription;
import java.util.Map;
import javax.annotation.Nullable;

public class TestCredentialSupport extends AbstractAtomicOperationsCredentialsSupport {
  @Nullable
  @Override
  public AtomicOperation<Void> convertOperation(Map<String, Object> input) {
    throw new UnsupportedOperationException();
  }

  @Override
  public OperationDescription convertDescription(Map<String, Object> input) {
    throw new UnsupportedOperationException();
  }
}
