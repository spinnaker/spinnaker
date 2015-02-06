/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.kato.gce.deploy.validators

import com.netflix.spinnaker.amos.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.amos.MapBackedAccountCredentialsRepository
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.amos.gce.GoogleNamedAccountCredentials
import com.netflix.spinnaker.kato.gce.deploy.description.ResizeGoogleReplicaPoolDescription
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class ResizeGoogleReplicaPoolDescriptionValidatorSpec extends Specification {
  private static final REPLICA_POOL_NAME = "spinnaker-test-v000"
  private static final DESIRED_NUM_REPLICAS = 5
  private static final ZONE = "us-central1-b"
  private static final ACCOUNT_NAME = "auto"

  @Shared
  ResizeGoogleReplicaPoolDescriptionValidator validator

  void setupSpec() {
    validator = new ResizeGoogleReplicaPoolDescriptionValidator()
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = Mock(GoogleNamedAccountCredentials)
    credentials.getName() >> ACCOUNT_NAME
    credentials.getCredentials() >> new GoogleCredentials()
    credentialsRepo.save(ACCOUNT_NAME, credentials)
    validator.accountCredentialsProvider = credentialsProvider
  }

  void "pass validation with proper description inputs"() {
    setup:
      def description = new ResizeGoogleReplicaPoolDescription(replicaPoolName: REPLICA_POOL_NAME,
                                                               numReplicas: DESIRED_NUM_REPLICAS,
                                                               zone: ZONE,
                                                               accountName: ACCOUNT_NAME)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "invalid numReplicas fails validation"() {
    setup:
      def description = new ResizeGoogleReplicaPoolDescription(numReplicas: -1)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue("numReplicas", "resizeGoogleReplicaPoolDescription.numReplicas.invalid")
  }

  void "null input fails validation"() {
    setup:
      def description = new ResizeGoogleReplicaPoolDescription()
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue('credentials', _)
      1 * errors.rejectValue('replicaPoolName', _)
      1 * errors.rejectValue('zone', _)
  }
}
