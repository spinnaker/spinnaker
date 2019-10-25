package com.netflix.spinnaker.keel.rest

import com.google.common.base.CaseFormat
import com.netflix.spinnaker.keel.api.Exportable
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.model.parseMoniker
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.supporting
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import kotlinx.coroutines.runBlocking
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/export"])
class ExportController(
  private val handlers: List<ResourceHandler<*, *>>,
  private val cloudDriverCache: CloudDriverCache
) {

  /**
   * Assist for mapping between Deck and Clouddriver cloudProvider names
   * and Keel's plugin namespace.
   */
  private val cloudProviderOverrides = mapOf(
    "aws" to "ec2"
  )

  /**
   * This route is location-less; given a resource name that can be monikered,
   * type, and account, all locations configured for the account are scanned for
   * matching resources that can be combined into a multi-region spec.
   *
   * Types are derived from Clouddriver's naming convention. It is assumed that
   * converting these to kebab case (i.e. securityGroups -> security-groups) will
   * match either the singular or plural of a [ResourceHandler]'s
   * [ResourceHandler.supportedKind].
   */
  @GetMapping(
    path = ["/{cloudProvider}/{account}/{type}/{name}"],
    params = ["serviceAccount"],
    produces = [MediaType.APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  fun get(
    @PathVariable("cloudProvider") cloudProvider: String,
    @PathVariable("account") account: String,
    @PathVariable("type") type: String,
    @PathVariable("name") name: String,
    @RequestParam("serviceAccount") serviceAccount: String
  ): SubmittedResource<*> {
    val apiVersion = SPINNAKER_API_V1.subApi(cloudProviderOverrides[cloudProvider] ?: cloudProvider)
    val kind = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, type)
    val handler = handlers.supporting(apiVersion, kind)
    val exportable = Exportable(
      account = account,
      serviceAccount = serviceAccount,
      moniker = parseMoniker(name),
      regions = (
        cloudDriverCache
          .credentialBy(account)
          .attributes["regions"] as List<Map<String, Any>>
        )
        .map { it["name"] as String }
        .toSet(),
      kind = kind
    )

    return runBlocking {
      handler.export(exportable)
    }
  }
}
