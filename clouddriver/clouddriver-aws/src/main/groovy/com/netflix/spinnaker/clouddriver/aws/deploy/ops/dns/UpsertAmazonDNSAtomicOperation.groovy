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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.dns

import software.amazon.awssdk.services.route53.model.Change
import software.amazon.awssdk.services.route53.model.ChangeAction
import software.amazon.awssdk.services.route53.model.ChangeBatch
import software.amazon.awssdk.services.route53.model.ChangeResourceRecordSetsRequest
import software.amazon.awssdk.services.route53.model.ResourceRecord
import software.amazon.awssdk.services.route53.model.ResourceRecordSet
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonDNSDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.UpsertAmazonLoadBalancerResult
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

    def route53 = amazonClientProvider.getAmazonRoute53V2(description.credentials, null)
    def hostedZone = route53.listHostedZones().hostedZones().find { it.name() == description.hostedZoneName }

    def recordSet = ResourceRecordSet.builder()
      .name(description.name)
      .type(description.type)
      .resourceRecords(ResourceRecord.builder().value(description.target).build())
      .ttl(60L)
      .build()
    def change = Change.builder().action(ChangeAction.UPSERT).resourceRecordSet(recordSet).build()
    def batch = ChangeBatch.builder().changes([change]).build()
    def request = ChangeResourceRecordSetsRequest.builder().hostedZoneId(hostedZone.id()).changeBatch(batch).build()

    task.updateStatus BASE_PHASE, "Upserting record..."
    route53.changeResourceRecordSets(request)
    task.updateStatus BASE_PHASE, "Upsertion complete."
    new UpsertAmazonDNSResult(dnsName: description.name)
  }
}
