package com.netflix.spinnaker.mort.aws.model

import com.netflix.spinnaker.mort.model.Subnet
import com.netflix.spinnaker.mort.model.SubnetProvider
import org.springframework.stereotype.Component

/**
 * Created by clin on 9/2/14.
 */
@Component
class AmazonSubnetProvider implements SubnetProvider {

  @Override
  Set<Subnet> getAll() {
    return null
  }

}
