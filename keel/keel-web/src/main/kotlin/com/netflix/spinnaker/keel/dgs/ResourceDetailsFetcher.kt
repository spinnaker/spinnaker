package com.netflix.spinnaker.keel.dgs

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.spinnaker.keel.graphql.DgsConstants
import com.netflix.spinnaker.keel.graphql.types.MdResource

/**
 * Fetches details about a specific resource
 */
@DgsComponent
class ResourceDetailsFetcher(
  private val applicationFetcherSupport: ApplicationFetcherSupport,
  private val yamlMapper: YAMLMapper
) {
  /**
   * Returns the raw definition of the resource in scope in YAML format. This will include metadata added by Keel.
   */
  @DgsData.List(
    DgsData(parentType = DgsConstants.MDRESOURCE.TYPE_NAME, field = DgsConstants.MDRESOURCE.RawDefinition),
    DgsData(parentType = DgsConstants.MD_RESOURCE.TYPE_NAME, field = DgsConstants.MD_RESOURCE.RawDefinition),
  )
  fun rawDefinition(dfe: DgsDataFetchingEnvironment): String? {
    val resource: MdResource = dfe.getSource()
    val config = applicationFetcherSupport.getDeliveryConfigFromContext(dfe)
    return config.resources.find { it.id == resource.id }
      ?.let {
        yamlMapper.writeValueAsString(it)
      }
  }
}
