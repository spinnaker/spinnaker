/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.clouddriver.titus.deploy.converters

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.titus.TitusOperation
import com.netflix.spinnaker.clouddriver.titus.caching.providers.TitusJobProvider
import com.netflix.spinnaker.clouddriver.titus.deploy.description.DestroyTitusJobDescription
import com.netflix.spinnaker.clouddriver.titus.deploy.ops.DestroyTitusJobAtomicOperation
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.util.logging.Slf4j
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
@TitusOperation(AtomicOperations.DESTROY_JOB)
class DestroyTitusJobAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  private final TitusJobProvider titusJobProvider

  @Autowired
  DestroyTitusJobAtomicOperationConverter(
    TitusJobProvider titusJobProvider
  ) {
    this.titusJobProvider = titusJobProvider
  }

  @Override
  AtomicOperation convertOperation(Map input) {
    new DestroyTitusJobAtomicOperation(convertDescription(input))
  }

  @Override
  DestroyTitusJobDescription convertDescription(Map input) {
    def converted = objectMapper.convertValue(input, DestroyTitusJobDescription)
    converted.credentials = getCredentialsObject(input.credentials as String)

    try {
      def job = titusJobProvider.collectJob(converted.credentials.name, converted.region, converted.jobId)
      converted.applications = [job.application] as Set
      converted.requiresApplicationRestriction = !converted.applications.isEmpty()
      converted.serverGroupName = job.name
    } catch (Exception e) {
      if (e instanceof StatusRuntimeException) {
        def statusRuntimeException = (StatusRuntimeException) e
        if (statusRuntimeException.status.code == Status.NOT_FOUND.code) {
          throw new NotFoundException(
            String.format(
              "Titus job not found (jobId: %s, account: %s, region: %s)",
              converted.jobId,
              converted.credentials.name,
              converted.region
            )
          )
        }
      }

      converted.applications = []
      converted.requiresApplicationRestriction = true
      log.error(
        "Unable to determine application for job (jobId: {}, account: {}, region: {})",
        converted.jobId,
        converted.credentials.name,
        converted.region,
        e
      )
    }

    return converted
  }
}
