package com.netflix.spinnaker.gate.plugins.web.installed

import com.netflix.spinnaker.gate.plugins.deck.DeckPluginService
import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import com.netflix.spinnaker.gate.services.internal.EchoService
import com.netflix.spinnaker.gate.services.internal.ExtendedFiatService
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.gate.services.internal.IgorService
import com.netflix.spinnaker.gate.services.internal.KeelService
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector
import com.netflix.spinnaker.gate.services.internal.RoscoService
import com.netflix.spinnaker.gate.services.internal.SwabbieService
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginManager
import com.netflix.spinnaker.kork.plugins.update.SpinnakerUpdateManager
import io.swagger.annotations.ApiOperation
import java.util.stream.Collectors
import org.pf4j.PluginWrapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import retrofit.RetrofitError

@RestController
@RequestMapping("/plugins/installed")
class PluginsInstalledController(
  private val clouddriverService: ClouddriverService,
  private val echoService: ObjectProvider<EchoService>,
  private val fiatService: ExtendedFiatService,
  private val front50Service: Front50Service,
  private val igorService: ObjectProvider<IgorService>,
  private val keelService: ObjectProvider<KeelService>,
  private val orcaServiceSelector: OrcaServiceSelector,
  private val roscoService: ObjectProvider<RoscoService>,
  private val swabbieService: ObjectProvider<SwabbieService>,
  private val spinnakerPluginManager: SpinnakerPluginManager,
  private val spinnakerUpdateManager: SpinnakerUpdateManager,
  private val deckPluginService: DeckPluginService
) {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @ApiOperation(value = "Get all installed Spinnaker plugins")
  @RequestMapping(method = [RequestMethod.GET])
  fun getInstalledPlugins(@RequestParam(value = "service", required = false) service: String?): Map<String, List<SpinnakerPluginDescriptor>> {
    return when (service) {
      clouddriver -> mutableMapOf(Pair(service, callService { clouddriverService.installedPlugins }))
      deck -> mutableMapOf(Pair(service, deckPlugins()))
      echo -> if (echoService.ifAvailable != null) mutableMapOf(Pair(service, callService { echoService.ifAvailable!!.installedPlugins })) else emptyMap()
      fiat -> mutableMapOf(Pair(service, callService { fiatService.installedPlugins }))
      front50 -> mutableMapOf(Pair(service, callService { front50Service.installedPlugins }))
      gate -> mutableMapOf(Pair(service, gatePlugins()))
      igor -> if (igorService.ifAvailable != null) mutableMapOf(Pair(service, callService { igorService.ifAvailable!!.installedPlugins })) else emptyMap()
      keel -> if (keelService.ifAvailable != null) mutableMapOf(Pair(service, callService { keelService.ifAvailable!!.installedPlugins })) else emptyMap()
      orca -> mutableMapOf(Pair(service, callService { orcaServiceSelector.select().installedPlugins }))
      rosco -> if (roscoService.ifAvailable != null) mutableMapOf(Pair(service, callService { roscoService.ifAvailable!!.installedPlugins })) else emptyMap()
      swabbie -> if (swabbieService.ifAvailable != null) mutableMapOf(Pair(service, callService { swabbieService.ifAvailable!!.installedPlugins })) else emptyMap()
      else -> {
        val echoPair = if (echoService.ifAvailable != null) Pair(echo, callService { echoService.ifAvailable!!.installedPlugins }) else Pair(echo, emptyList())
        val igorPair = if (igorService.ifAvailable != null) Pair(igor, callService { igorService.ifAvailable!!.installedPlugins }) else Pair(igor, emptyList())
        val keelPair = if (keelService.ifAvailable != null) Pair(keel, callService { keelService.ifAvailable!!.installedPlugins }) else Pair(keel, emptyList())
        val roscoPair = if (roscoService.ifAvailable != null) Pair(rosco, callService { roscoService.ifAvailable!!.installedPlugins }) else Pair(rosco, emptyList())
        val swabbiePair = if (swabbieService.ifAvailable != null) Pair(swabbie, callService { swabbieService.ifAvailable!!.installedPlugins }) else Pair(swabbie, emptyList())
        mutableMapOf(
          Pair(clouddriver, callService { clouddriverService.installedPlugins }),
          Pair(deck, deckPlugins()),
          echoPair,
          Pair(fiat, callService { fiatService.installedPlugins }),
          Pair(front50, callService { front50Service.installedPlugins }),
          Pair(gate, gatePlugins()),
          igorPair,
          keelPair,
          Pair(orca, callService { orcaServiceSelector.select().installedPlugins }),
          roscoPair,
          swabbiePair
        ).toSortedMap()
      }
    }
  }

  fun gatePlugins(): List<SpinnakerPluginDescriptor> {
    return spinnakerPluginManager.plugins.stream()
      .map { obj: PluginWrapper -> obj.descriptor as SpinnakerPluginDescriptor }
      .collect(Collectors.toList())
  }

  /**
   * Map deck plugins to PF4J plugin descriptors.  Deck plugins are not PF4J runtime plugins,
   * but the properties from the plugin info can be mapped correctly.
   */
  fun deckPlugins(): List<SpinnakerPluginDescriptor> {
    val deckPlugins = deckPluginService.getPluginsManifests()
    val plugins = spinnakerUpdateManager.plugins
    val descriptors: MutableList<SpinnakerPluginDescriptor> = mutableListOf()

    for (deckPlugin in deckPlugins) {
      val plugin = plugins.stream()
        .filter { it.id == deckPlugin.id }
        .findFirst()
        .get()

      val release = plugin.getReleases().stream()
        .filter { it.version == deckPlugin.version }
        .findFirst()
        .get()

      descriptors.add(SpinnakerPluginDescriptor(
        pluginId = plugin.id,
        pluginDescription = plugin.description,
        version = release.version,
        requires = release.requires,
        provider = plugin.provider
      ))
    }

    return descriptors
  }

  fun callService(call: () -> List<SpinnakerPluginDescriptor>): List<SpinnakerPluginDescriptor> {
    return try {
      call()
    } catch (e: RetrofitError) {
      log.warn("Unable to retrieve installed plugins from '${e.response?.url}' due to '${e.response?.status}'")
      return emptyList()
    }
  }

  companion object {
    const val clouddriver: String = "clouddriver"
    const val deck: String = "deck"
    const val echo: String = "echo"
    const val fiat: String = "fiat"
    const val front50: String = "front50"
    const val gate: String = "gate"
    const val igor: String = "igor"
    const val keel: String = "keel"
    const val orca: String = "orca"
    const val rosco: String = "rosco"
    const val swabbie: String = "swabbie"
  }
}
