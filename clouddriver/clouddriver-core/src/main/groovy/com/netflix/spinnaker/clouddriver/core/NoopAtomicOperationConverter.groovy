/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.core

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationConverter
import com.netflix.spinnaker.orchestration.OperationDescription
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component("noProvidersConfigured")
class NoopAtomicOperationConverter implements AtomicOperationConverter {

  NoopAtomicOperationConverter() {
    log.warn("No AtomicOperationConverters found. This likely means you have no cloud providers configured.")
  }

  @Override
  AtomicOperation convertOperation(Map input) {
    return null
  }

  @Override
  OperationDescription convertDescription(Map input) {
    return null
  }
}
