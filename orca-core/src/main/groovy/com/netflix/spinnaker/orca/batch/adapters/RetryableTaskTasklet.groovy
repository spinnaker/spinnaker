/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.batch.adapters

import com.netflix.spinnaker.orca.RetryableTask
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.repeat.RepeatStatus

class RetryableTaskTasklet extends TaskTasklet {

  RetryableTaskTasklet(RetryableTask task) {
    super(task)
  }

  @Override
  RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
      def status = super.execute(contribution, chunkContext)
      if (status.continuable) {
        // I hate having to do this but it's how Spring's retry template works and
        // believe it or not that's the only way to delay between executions of a
        // tasklet
        throw new RuntimeException()
      } else {
        return status
      }
  }
}
