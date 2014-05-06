package com.netflix.kato.deploy.aws.ops.dns

import com.amazonaws.services.route53.model.AliasTarget
import com.amazonaws.services.route53.model.Change
import com.amazonaws.services.route53.model.ChangeAction
import com.amazonaws.services.route53.model.ChangeBatch
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest
import com.amazonaws.services.route53.model.ResourceRecord
import com.amazonaws.services.route53.model.ResourceRecordSet
import com.netflix.kato.data.task.Task
import com.netflix.kato.data.task.TaskRepository
import com.netflix.kato.deploy.aws.description.UpsertAmazonDNSDescription
import com.netflix.kato.deploy.aws.ops.loadbalancer.CreateAmazonLoadBalancerResult
import com.netflix.kato.orchestration.AtomicOperation


import static com.netflix.kato.deploy.aws.StaticAmazonClients.getAmazonRoute53

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
