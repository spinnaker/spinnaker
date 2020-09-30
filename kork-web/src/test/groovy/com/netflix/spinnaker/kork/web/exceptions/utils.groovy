/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.kork.web.exceptions

import com.netflix.spinnaker.kork.api.exceptions.ExceptionDetails
import com.netflix.spinnaker.kork.api.exceptions.ExceptionMessage
import com.netflix.spinnaker.kork.exceptions.SpinnakerException
import org.springframework.beans.BeansException
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

import javax.annotation.Nonnull
import javax.annotation.Nullable

class ExceptionMessageProvider implements ObjectProvider<List<ExceptionMessage>> {

  List<ExceptionMessage> exceptionMessages

  ExceptionMessageProvider(List<ExceptionMessage> exceptionMessages) {
    this.exceptionMessages = exceptionMessages
  }

  @Override
  List<ExceptionMessage> getObject(Object... args) throws BeansException {
    return exceptionMessages
  }

  @Override
  List<ExceptionMessage> getIfAvailable() throws BeansException {
    return exceptionMessages
  }

  @Override
  List<ExceptionMessage> getIfUnique() throws BeansException {
    return exceptionMessages
  }

  @Override
  List<ExceptionMessage> getObject() throws BeansException {
    return exceptionMessages
  }
}

class AccessDeniedExceptionMessage implements ExceptionMessage {

  private String messageToBeAppended

  AccessDeniedExceptionMessage(String messageToBeAppended) {
    this.messageToBeAppended = messageToBeAppended
  }

  @Override
  Optional<String> message(Throwable throwable, @Nullable ExceptionDetails exceptionDetails) {
    if (throwable.getClass() == LocalAccessDeniedException.class) {
      return Optional.of(messageToBeAppended)
    }
    return Optional.empty()
  }

  @Override
  Optional<String> message(String errorCode, @Nullable ExceptionDetails exceptionDetails) {
    if (errorCode == "authorization") {
      return Optional.of(messageToBeAppended)
    }
    return Optional.empty()
  }
}

@ResponseStatus(value = HttpStatus.FORBIDDEN, reason = "Access is denied")
class LocalAccessDeniedException extends SpinnakerException {
  LocalAccessDeniedException() {}
  LocalAccessDeniedException(String message) {
    super(message)
  }
}

