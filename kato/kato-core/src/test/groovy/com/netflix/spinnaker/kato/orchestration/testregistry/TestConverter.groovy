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

package com.netflix.spinnaker.kato.orchestration.testregistry

import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.orchestration.AtomicOperationConverter
import com.netflix.spinnaker.kato.orchestration.AtomicOperationDescription
import org.springframework.stereotype.Component

/**
 * @author sthadeshwar
 */
@Component("operationOldDescription")
@TestProvider
@AtomicOperationDescription("operationDescription")
class TestConverter implements AtomicOperationConverter {
  @Override
  AtomicOperation convertOperation(Map input) {
    return null
  }

  @Override
  Object convertDescription(Map input) {
    return null
  }
}
