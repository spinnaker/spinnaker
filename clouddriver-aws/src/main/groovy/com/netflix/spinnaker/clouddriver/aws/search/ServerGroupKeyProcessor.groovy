package com.netflix.spinnaker.clouddriver.aws.search

import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.aws.model.AmazonServerGroup
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonClusterProvider
import com.netflix.spinnaker.clouddriver.cache.KeyProcessor
import com.netflix.spinnaker.clouddriver.model.view.ServerGroupViewModelPostProcessor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component("AmazonServerGroupKeyProcessor")
class ServerGroupKeyProcessor implements KeyProcessor {

  @Autowired
  AmazonClusterProvider amazonClusterProvider

  @Autowired(required = false)
  ServerGroupViewModelPostProcessor serverGroupViewModelPostProcessor

  @Override
  Boolean canProcess(String type) {
    return type == "serverGroups"
  }

  @Override
  Boolean exists(String key) {

    Map<String, String> parsed = Keys.parse(key)
    String account = parsed['account']
    String region = parsed['region']
    String name = parsed['serverGroup']

    return amazonClusterProvider.getServerGroup(account, region, name) != null
  }
}
