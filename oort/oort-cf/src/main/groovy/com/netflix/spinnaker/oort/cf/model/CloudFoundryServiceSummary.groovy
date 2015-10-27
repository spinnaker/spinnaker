package com.netflix.spinnaker.oort.cf.model

import com.netflix.spinnaker.mort.model.SecurityGroupSummary

/**
 * @author Greg Turnquist
 */
class CloudFoundryServiceSummary implements SecurityGroupSummary {

  String name
  String id
}
