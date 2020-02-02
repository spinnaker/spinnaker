/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.q.audit

import com.netflix.spinnaker.kork.common.Header
import com.netflix.spinnaker.orca.q.ApplicationAware
import com.netflix.spinnaker.orca.q.ExecutionLevel
import com.netflix.spinnaker.orca.q.StageLevel
import com.netflix.spinnaker.orca.q.TaskLevel
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.MessageHandler
import org.slf4j.MDC
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.stereotype.Component

@Component
class ExecutionTrackingMessageHandlerPostProcessor : BeanPostProcessor {
  override fun postProcessBeforeInitialization(bean: Any, beanName: String) =
    bean

  override fun postProcessAfterInitialization(bean: Any, beanName: String) =
    if (bean is MessageHandler<*>) {
      ExecutionTrackingMessageHandlerProxy(bean)
    } else {
      bean
    }

  private class ExecutionTrackingMessageHandlerProxy<M : Message>(
    private val delegate: MessageHandler<M>
  ) : MessageHandler<M> by delegate {
    override fun invoke(message: Message) {
      try {
        when (message) {
          is TaskLevel -> {
            MDC.put(
              Header.EXECUTION_ID.header,
              "${message.executionId}:${message.stageId}:${message.taskId}")
          }
          is StageLevel -> {
            MDC.put(
              Header.EXECUTION_ID.header,
              "${message.executionId}:${message.stageId}")
          }
          is ExecutionLevel -> {
            MDC.put(
              Header.EXECUTION_ID.header,
              message.executionId)
          }
        }

        if (message is ApplicationAware) {
          MDC.put(Header.APPLICATION.header, message.application)
        }

        delegate.invoke(message)
      } finally {
        MDC.remove(Header.EXECUTION_ID.header)
        MDC.remove(Header.APPLICATION.header)
      }
    }
  }
}
