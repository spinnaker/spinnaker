/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.orchestration

import com.netflix.spinnaker.kork.exceptions.HasAdditionalAttributes
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.BAD_REQUEST)
class AtomicOperationException extends RuntimeException implements HasAdditionalAttributes {
  List<String> errors

  AtomicOperationException(String message, List<String> errors) {
    super(message)
    this.errors = errors
  }

  @Override
  Map<String, Object> getAdditionalAttributes() {
    return errors ? ["errors": errors] : [:]
  }
}
