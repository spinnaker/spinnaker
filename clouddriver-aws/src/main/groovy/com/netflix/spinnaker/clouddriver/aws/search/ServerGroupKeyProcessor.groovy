package com.netflix.spinnaker.clouddriver.aws.search

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.cache.KeyProcessor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.SERVER_GROUPS

@Component("AmazonServerGroupKeyProcessor")
class ServerGroupKeyProcessor implements KeyProcessor {

  @Autowired
  private final Cache cacheView

  @Override
  Boolean canProcess(String type) {
    return type == "serverGroups"
  }

  @Override
  Boolean exists(String serverGroupKey) {
    return cacheView.get(SERVER_GROUPS.ns, serverGroupKey, RelationshipCacheFilter.none()) != null
  }
}
