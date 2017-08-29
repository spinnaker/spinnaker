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

package com.netflix.spinnaker.orca.q.handler

import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.CancelStage
import com.netflix.spinnaker.orca.q.MessageHandler
import com.netflix.spinnaker.orca.q.Queue
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.concurrent.Executor

@Component
class CancelStageHandler(
  override val queue: Queue,
  override val repository: ExecutionRepository,
  override val stageDefinitionBuilders: Collection<StageDefinitionBuilder>,
  @Qualifier("messageHandlerPool") private val executor: Executor
) : MessageHandler<CancelStage>, StageBuilderAware {

  override val messageType = CancelStage::class.java

  override fun handle(message: CancelStage) {
    message.withStage { stage ->
      if (stage.getStatus().isHalt) {
        stage.builder().let { builder ->
          if (builder is CancellableStage) {
            // for the time being we execute this off-thread as some cancel
            // routines may run long enough to cause message acknowledgment to
            // time out.
            executor.execute {
              builder.cancel(stage)
            }
          }
        }
      }
    }
  }
}
