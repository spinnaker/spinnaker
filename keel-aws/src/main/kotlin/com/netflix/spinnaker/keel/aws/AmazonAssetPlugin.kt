package com.netflix.spinnaker.keel.aws

import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.AssetContainer
import com.netflix.spinnaker.keel.api.TypeMetadata
import com.netflix.spinnaker.keel.api.plugin.ConvergeResponse
import com.netflix.spinnaker.keel.api.plugin.CurrentResponse
import com.netflix.spinnaker.keel.aws.SecurityGroupRule.RuleCase.CIDRRULE
import com.netflix.spinnaker.keel.aws.SecurityGroupRule.RuleCase.HTTPRULE
import com.netflix.spinnaker.keel.aws.SecurityGroupRule.RuleCase.REFERENCERULE
import com.netflix.spinnaker.keel.aws.SecurityGroupRule.RuleCase.RULE_NOT_SET
import com.netflix.spinnaker.keel.aws.SecurityGroupRule.RuleCase.SELFREFERENCINGRULE
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.plugin.AssetPlugin
import com.netflix.spinnaker.keel.proto.isA
import com.netflix.spinnaker.keel.proto.pack
import com.netflix.spinnaker.keel.proto.unpack
import io.grpc.stub.StreamObserver
import org.lognet.springboot.grpc.GRpcService

@GRpcService
class AmazonAssetPlugin(
  private val cloudDriverService: CloudDriverService,
  private val cloudDriverCache: CloudDriverCache,
  private val orcaService: OrcaService
) : AssetPlugin() {

  override val supportedTypes: Iterable<TypeMetadata> = setOf(
    "aws.SecurityGroup",
    "aws.ClassicLoadBalancer"
  ).map { kind ->
    TypeMetadata.newBuilder().setApiVersion("1.0").setKind(kind).build()
  }

  override fun current(request: AssetContainer, responseObserver: StreamObserver<CurrentResponse>) {
    val asset = when {
      request.asset.spec.isA<SecurityGroup>() -> {
        val spec: SecurityGroup = request.asset.spec.unpack()
        cloudDriverService.getSecurityGroup(spec)
      }
      else -> null
    }

    with(responseObserver) {
      onNext(CurrentResponse
        .newBuilder()
        .also {
          it.current = asset?.toProto(request)
          it.desired = request.asset
        }
        .build())
      onCompleted()
    }
  }

  override fun converge(request: AssetContainer, responseObserver: StreamObserver<ConvergeResponse>) {
    when {
      request.asset.spec.isA<SecurityGroup>() -> {
        val spec: SecurityGroup = request.asset.spec.unpack()
        orcaService
          .orchestrate(OrchestrationRequest(
            "Upsert security group $request.",
            spec.application,
            "Upsert security group $request.",
            listOf(Job(
              "upsertSecurityGroup",
              mutableMapOf(
                "application" to spec.application,
                "credentials" to spec.accountName,
                "cloudProvider" to "aws",
                "name" to spec.name,
                "regions" to listOf(spec.region),
                "vpcId" to spec.vpcName,
                "description" to spec.description,
                "securityGroupIngress" to portRangeRuleToJob(spec),
                "ipIngress" to spec.inboundRulesList.filter { it.hasCidrRule() }.flatMap {
                  convertCidrRuleToJob(it)
                },
                "accountName" to spec.accountName
              )
            )),
            OrchestrationTrigger("1")
          ))
      }
    }

    with(responseObserver) {
      onNext(ConvergeResponse.newBuilder().build())
      onCompleted()
    }
  }

  // TODO: feels like these things should be further extracted / abstracted out of this class (as and when needed elsewhere)

  private fun CloudDriverService.getSecurityGroup(spec: SecurityGroup): com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup =
    getSecurityGroup(
      spec.accountName,
      CLOUD_PROVIDER,
      spec.name,
      spec.region,
      spec.vpcName?.let { cloudDriverCache.networkBy(it, spec.accountName, spec.region).id }
    )

  private fun com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup.toProto(request: AssetContainer): Asset =
    Asset.newBuilder()
      .apply {
        if (request.hasAsset()) {
          typeMetadata = request.asset.typeMetadata
          spec = SecurityGroup.newBuilder()
            .also {
              it.name = name
              it.accountName = accountName
              it.region = region
              it.vpcName = vpcId?.let { cloudDriverCache.networkBy(it).name }
              it.description = description
            }
            .build()
            .pack()
        }
      }
      .build()
}

typealias JobRules = List<MutableMap<String, Any?>>

private fun portRangeRuleToJob(spec: SecurityGroup): JobRules {
  val portRanges = spec.inboundRulesList.flatMap { rule ->
    when {
      rule.hasReferenceRule() -> rule.referenceRule.portRangesList
      rule.hasSelfReferencingRule() -> rule.selfReferencingRule.portRangesList
      else -> emptyList()
    }
  }
  return spec
    .inboundRulesList
    .filter { it.hasReferenceRule() || it.hasSelfReferencingRule() }
    .flatMap { rule ->
      when {
        rule.hasReferenceRule() -> rule.referenceRule.portRangesList
        rule.hasSelfReferencingRule() -> rule.selfReferencingRule.portRangesList
        else -> emptyList()
      }
        .map { Pair(rule, it) }
    }
    .map { (rule, ports) ->
      mutableMapOf<String, Any?>(
        "type" to rule.protocol,
        "startPort" to ports.startPort,
        "endPort" to ports.endPort,
        "name" to if (rule.hasReferenceRule()) rule.referenceRule.name else spec.name
      )
      // TODO: need to handle cross account something like this...
//      .let { m ->
//        if (rule is CrossAccountReferenceSecurityGroupRule) {
//          changeSummary.addMessage("Adding cross account reference support account ${rule.account}")
//          m["accountName"] = rule.account
//          m["crossAccountEnabled"] = true
//          m["vpcId"] = clouddriverCache.networkBy(rule.vpcName, spec.accountName, spec.region)
//        }
//        m
//      }
    }
}

private fun convertCidrRuleToJob(rule: SecurityGroupRule): JobRules =
  when {
    rule.hasCidrRule() -> rule.cidrRule.portRangesList.map { ports ->
      mutableMapOf(
        "type" to rule.protocol,
        "startPort" to ports.startPort,
        "endPort" to ports.endPort,
        "cidr" to rule.cidrRule.blockRange
      )
    }
    else -> emptyList()
  }

private val SecurityGroupRule.protocol: String
  get() = when (ruleCase) {
    CIDRRULE -> cidrRule.protocol
    SELFREFERENCINGRULE -> selfReferencingRule.protocol
    REFERENCERULE -> referenceRule.protocol
    HTTPRULE -> "http" // TODO: can't this be https as well?
    RULE_NOT_SET -> "unknown"
  }
