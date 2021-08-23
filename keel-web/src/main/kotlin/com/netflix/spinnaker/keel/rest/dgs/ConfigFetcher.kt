package com.netflix.spinnaker.keel.rest.dgs

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.spinnaker.keel.graphql.DgsConstants
import com.netflix.spinnaker.keel.graphql.types.MdConfig
import com.netflix.spinnaker.keel.igor.DeliveryConfigImporter

/**
 * Fetches details about the application's delivery config
 */
@DgsComponent
class ConfigFetcher(
  private val applicationFetcherSupport: ApplicationFetcherSupport,
  private val yamlMapper: YAMLMapper,
  private val deliveryConfigImporter: DeliveryConfigImporter
) {

  @DgsData(parentType = DgsConstants.MDAPPLICATION.TYPE_NAME, field = DgsConstants.MDAPPLICATION.Config)
  fun config(dfe: DgsDataFetchingEnvironment): MdConfig {
    val config = applicationFetcherSupport.getDeliveryConfigFromContext(dfe)
    return MdConfig(
      id = "${config.application}-${config.name}",
      updatedAt = config.updatedAt,
      rawConfig = config.rawConfig,
      processedConfig = yamlMapper.writeValueAsString(config.copy(rawConfig = null))
    )
  }

  @DgsData(parentType = DgsConstants.MDCONFIG.TYPE_NAME, field = DgsConstants.MDCONFIG.RawConfig)
  fun rawConfig(dfe: DgsDataFetchingEnvironment): String? {
    val rawConfig = dfe.getSource<MdConfig>().rawConfig
    val config = applicationFetcherSupport.getDeliveryConfigFromContext(dfe)
    // If the raw config is empty or if it was imported via orca (orca adds the gitMetadata to the metadata) we fetch it again from stash
    // TODO: remove this once we removed the import pipeline completely
    return if (rawConfig.isNullOrBlank() || config.metadata.containsKey("gitMetadata")) {
      deliveryConfigImporter.import(config.application, addMetadata = false).rawConfig
    } else {
      rawConfig
    }
  }
}
