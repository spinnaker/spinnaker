package com.netflix.spinnaker.clouddriver.dcos.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerLbId
import com.netflix.spinnaker.clouddriver.dcos.model.DcosLoadBalancer
import com.netflix.spinnaker.clouddriver.dcos.model.DcosServerGroup
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import mesosphere.marathon.client.model.v2.App
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.naming.OperationNotSupportedException

import static com.netflix.spinnaker.clouddriver.dcos.provider.DcosProviderUtils.*

@Component
class DcosLoadBalancerProvider implements LoadBalancerProvider<DcosLoadBalancer> {

  final String cloudProvider = DcosCloudProvider.ID

  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  DcosLoadBalancerProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  Set<DcosLoadBalancer> getApplicationLoadBalancers(String applicationName) {

    String applicationKey = Keys.getApplicationKey(applicationName)
    CacheData application = cacheView.get(Keys.Namespace.APPLICATIONS.ns, applicationKey)

    def loadBalancers = getCachedLoadBalancers(application, applicationName)

    Set<CacheData> allServerGroups = resolveRelationshipDataForCollection(cacheView, loadBalancers, Keys.Namespace.SERVER_GROUPS.ns)
    Map<String, DcosServerGroup> serverGroupsByCacheKey = allServerGroups.collectEntries { serverGroupData ->
      def serverGroup = objectMapper.convertValue(serverGroupData.attributes.serverGroup, DcosServerGroup)
      return [(serverGroupData.id): serverGroup]
    }

    return loadBalancers.collect {
      translateLoadBalancer(it, serverGroupsByCacheKey)
    } as Set
  }

  @Override
  List<DcosLoadBalancer> list() {
    Collection<String> loadBalancers = cacheView.getIdentifiers(Keys.Namespace.LOAD_BALANCERS.ns)
    loadBalancers.findResults {
      def parse = Keys.parse(it)
      parse ? new DcosLoadBalancer(parse.name, parse.region, parse.account) : null
    }
  }

  // TODO: Implement if/when these methods are needed in Deck.
  @Override
  LoadBalancerProvider.Item get(String name) {
    throw new OperationNotSupportedException("Not implemented.")
  }

  @Override
  List<LoadBalancerProvider.Details> byAccountAndRegionAndName(String account,
                                                               String region,
                                                               String name) {
    throw new OperationNotSupportedException("Not implemented.")
  }

  private Collection<CacheData> getCachedLoadBalancers(CacheData application, String applicationName) {
    Set<String> loadBalancerKeys = []

    def applicationServerGroups = application ? resolveRelationshipData(cacheView, application, Keys.Namespace.SERVER_GROUPS.ns) : []
    applicationServerGroups.each { CacheData serverGroup ->
      loadBalancerKeys.addAll(serverGroup.relationships[Keys.Namespace.LOAD_BALANCERS.ns] ?: [])
    }

    loadBalancerKeys.addAll(cacheView.filterIdentifiers(Keys.Namespace.LOAD_BALANCERS.ns,
            Keys.getLoadBalancerKey("*", '*', combineAppStackDetail(applicationName, '*', null))))
    loadBalancerKeys.addAll(cacheView.filterIdentifiers(Keys.Namespace.LOAD_BALANCERS.ns,
            Keys.getLoadBalancerKey("*", '*', combineAppStackDetail(applicationName, null, null))))

    return cacheView.getAll(Keys.Namespace.LOAD_BALANCERS.ns, loadBalancerKeys)
  }

  private DcosLoadBalancer translateLoadBalancer(CacheData loadBalancerEntry,
                                                 Map<String, DcosServerGroup> serverGroupsByCacheKey) {

    def parts = Keys.parse(loadBalancerEntry.id)

    App app = objectMapper.convertValue(loadBalancerEntry.attributes.app, App)

    List<DcosServerGroup> serverGroups = []
    loadBalancerEntry.relationships[Keys.Namespace.SERVER_GROUPS.ns]?.each { String serverGroupKey ->
      DcosServerGroup serverGroup = serverGroupsByCacheKey[serverGroupKey]
      if (serverGroup) {
        serverGroups << serverGroup
      }
    }

    return new DcosLoadBalancer(parts.account, parts.region, app, serverGroups)
  }
}
