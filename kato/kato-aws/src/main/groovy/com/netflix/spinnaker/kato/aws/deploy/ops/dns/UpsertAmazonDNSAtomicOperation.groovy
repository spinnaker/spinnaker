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

package com.netflix.spinnaker.kato.aws.deploy.ops.dns

import com.amazonaws.services.route53.model.*
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.aws.deploy.description.UpsertAmazonDNSDescription
import com.netflix.spinnaker.kato.aws.deploy.ops.loadbalancer.UpsertAmazonLoadBalancerResult
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class UpsertAmazonDNSAtomicOperation implements AtomicOperation<UpsertAmazonDNSResult> {
  private static final String BASE_PHASE = "UPSERT_DNS"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  UpsertAmazonDNSDescription description

  UpsertAmazonDNSAtomicOperation(UpsertAmazonDNSDescription description) {
    this.description = description
  }

  @Override
  UpsertAmazonDNSResult operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Amazon DNS Upsert"

    def priorElb = priorOutputs.find { it instanceof UpsertAmazonLoadBalancerResult } as UpsertAmazonLoadBalancerResult

    if (priorElb && !description.target) {
      task.updateStatus BASE_PHASE, "No target specified. Assuming target of prior ELB deployment."
      description.target = priorElb.loadBalancers?.values()?.getAt(0)?.dnsName
    }

    def route53 = amazonClientProvider.getAmazonRoute53(description.credentials, null, true)
    def hostedZone = route53.listHostedZones().hostedZones.find { it.name == description.hostedZoneName }

    def recordSet = new ResourceRecordSet(description.name, description.type)
      .withResourceRecords(new ResourceRecord(description.target)).withTTL(60)
    def change = new Change(action: ChangeAction.UPSERT, resourceRecordSet: recordSet)
    def batch = new ChangeBatch([change])
    def request = new ChangeResourceRecordSetsRequest(hostedZone.id, batch)

    task.updateStatus BASE_PHASE, "Upserting record..."
    route53.changeResourceRecordSets(request)
    task.updateStatus BASE_PHASE, "Upsertion complete."
    new UpsertAmazonDNSResult(dnsName: description.name)
  }
}
