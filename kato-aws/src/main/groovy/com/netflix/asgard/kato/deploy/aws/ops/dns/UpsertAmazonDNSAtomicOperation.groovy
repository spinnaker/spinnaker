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

package com.netflix.asgard.kato.deploy.aws.ops.dns

import com.amazonaws.services.route53.model.*
import com.netflix.asgard.kato.data.task.Task
import com.netflix.asgard.kato.data.task.TaskRepository
import com.netflix.asgard.kato.deploy.aws.description.UpsertAmazonDNSDescription
import com.netflix.asgard.kato.deploy.aws.ops.loadbalancer.CreateAmazonLoadBalancerResult
import com.netflix.asgard.kato.orchestration.AtomicOperation

import static com.netflix.asgard.kato.deploy.aws.StaticAmazonClients.getAmazonRoute53

class UpsertAmazonDNSAtomicOperation implements AtomicOperation<UpsertAmazonDNSResult> {
  private static final String BASE_PHASE = "CREATE_ELB"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  UpsertAmazonDNSDescription description

  UpsertAmazonDNSAtomicOperation(UpsertAmazonDNSDescription description) {
    this.description = description
  }

  @Override
  UpsertAmazonDNSResult operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Amazon DNS Upsert"

    def priorElb = priorOutputs.find { it instanceof CreateAmazonLoadBalancerResult } as CreateAmazonLoadBalancerResult

    if (priorElb && !description.target) {
      task.updateStatus BASE_PHASE, " > No target specified. Assuming target of prior ELB deployment."
      description.target = priorElb.loadBalancers?.values()?.getAt(0)?.dnsName
    }

    def route53 = getAmazonRoute53(description.credentials, null)
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
