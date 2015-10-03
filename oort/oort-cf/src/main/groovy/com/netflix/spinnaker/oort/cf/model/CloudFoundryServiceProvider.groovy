package com.netflix.spinnaker.oort.cf.model

import com.netflix.spinnaker.clouddriver.cf.CloudFoundryCloudProvider
import com.netflix.spinnaker.mort.model.SecurityGroupProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * @author Greg Turnquist
 */
@Component
class CloudFoundryServiceProvider implements SecurityGroupProvider<CloudFoundryService> {

  @Autowired
  CloudFoundryCloudProvider cloudFoundryCloudProvider

  @Autowired
  CloudFoundryResourceRetriever cloudFoundryResourceRetriever

  @Override
  String getType() {
    cloudFoundryCloudProvider.id
  }

  @Override
  Set<CloudFoundryService> getAll(boolean includeRules) {
    Set<CloudFoundryService> services = [] as Set<CloudFoundryService>
    cloudFoundryResourceRetriever.servicesByAccount.values().each {
      services.addAll(it)
    }
    services
  }

  @Override
  Set<CloudFoundryService> getAllByRegion(boolean includeRules, String region) {
    cloudFoundryResourceRetriever.servicesByRegion[region]
  }

  @Override
  Set<CloudFoundryService> getAllByAccount(boolean includeRules, String account) {
    cloudFoundryResourceRetriever.servicesByAccount[account]
  }

  @Override
  Set<CloudFoundryService> getAllByAccountAndName(boolean includeRules, String account, String name) {
    cloudFoundryResourceRetriever.servicesByAccount[account].findAll {it.name == name}
  }

  @Override
  Set<CloudFoundryService> getAllByAccountAndRegion(boolean includeRule, String account, String region) {
    cloudFoundryResourceRetriever.servicesByAccount[account].findAll {it.region == region}
  }

  @Override
  CloudFoundryService get(String account, String region, String name, String vpcId) {
    cloudFoundryResourceRetriever.servicesByAccount[account].find {
      it.name == name && it.region == region
    }
  }
}
