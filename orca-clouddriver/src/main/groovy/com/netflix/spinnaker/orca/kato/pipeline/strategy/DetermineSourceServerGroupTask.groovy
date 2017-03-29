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

package com.netflix.spinnaker.orca.kato.pipeline.strategy

import java.util.concurrent.TimeUnit
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
@Slf4j
// TODO: This should all be made less AWS-specific.
class DetermineSourceServerGroupTask implements RetryableTask {

  static final int MAX_ATTEMPTS = 10
  static final int MIN_CONSECUTIVE_404 = 3

  final long backoffPeriod = TimeUnit.SECONDS.toMillis(5)

  // not expecting to timeout, we will fail after MAX_ATTEMPTS:
  final long timeout = TimeUnit.SECONDS.toMillis(300)

  @Autowired
  SourceResolver sourceResolver

  @Override
  TaskResult execute(Stage stage) {
    def stageData = stage.mapTo(StageData)
    if (!stageData.source && !stageData.region && !stageData.availabilityZones) {
      throw new IllegalStateException("No 'source' or 'region' or 'availabilityZones' in stage context")
    }
    Exception lastException = null
    try {
      def source = sourceResolver.getSource(stage)
      Boolean useSourceCapacity = useSourceCapacity(stage, source)
      if (useSourceCapacity && !source) {
        throw new IllegalStateException( "useSourceCapacity requested, but unable to determine source for ${stageData.account}/${stageData.availabilityZones?.keySet()?.getAt(0)}/${stageData.cluster}")
      }
      def stageOutputs = [:]
      if (source) {
        stageOutputs.source = [
          asgName          : source.asgName,
          serverGroupName  : source.serverGroupName,
          account          : source.account,
          region           : source.region,
          useSourceCapacity: useSourceCapacity
        ]
      }
      return new TaskResult(ExecutionStatus.SUCCEEDED, stageOutputs)
    } catch (ex) {
      log.warn("${getClass().simpleName} failed with $ex.message on attempt ${stage.context.attempt ?: 1}")
      lastException = ex
    }

    Boolean useSourceCapacity = useSourceCapacity(stage, null)
    boolean isNotFound = (lastException instanceof RetrofitError && lastException.kind == RetrofitError.Kind.HTTP && lastException.response.status == 404)

    StringWriter sw = new StringWriter()
    sw.append(lastException.message).append("\n")
    new PrintWriter(sw).withWriter { lastException.printStackTrace(it as PrintWriter) }

    def ctx = [
      lastException: sw.toString(),
      attempt: (stage.context.attempt ?: 1) + 1,
      consecutiveNotFound: isNotFound ? (stage.context.consecutiveNotFound ?: 0) + 1 : 0
    ]

    if (ctx.consecutiveNotFound >= MIN_CONSECUTIVE_404 && !useSourceCapacity) {
      //we aren't asking to use the source capacity for sizing and have got some repeated 404s on the cluster - assume that is a legit
      return new TaskResult(ExecutionStatus.SUCCEEDED, ctx)
    }

    if (ctx.attempt <= MAX_ATTEMPTS) {
      return new TaskResult(ExecutionStatus.RUNNING, ctx)
    }

    throw new IllegalStateException(lastException.getMessage(), lastException)
  }

  Boolean useSourceCapacity(Stage stage, StageData.Source source) {
    if (source?.useSourceCapacity != null) return source.useSourceCapacity
    if (stage.context.useSourceCapacity != null) return (stage.context.useSourceCapacity as Boolean)
    return null
  }
}
