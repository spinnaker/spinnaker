package com.netflix.spinnaker.clouddriver.dcos.deploy.util.id

import spock.lang.Specification
import spock.lang.Unroll

class DcosSpinnakerLbIdSpec extends Specification {
  static final def ACCOUNT = "spinnaker"
  static final def REGION = "us-west-1"
  static final def LOAD_BALANCER = "loadbalancer"
  static final def INVALID_ACCOUNT = "invalid-account"
  static final def INVALID_MARATHON_PART = "-iNv.aLiD-"

  @Unroll
  void "static factory method should return an empty optional given invalid marathon app id \"#appId\""() {
    expect:
    def dcosPath = DcosSpinnakerLbId.parseVerbose(appId, ACCOUNT)
    dcosPath == Optional.empty()

    where:
    appId << [
      "/",
      "",
      "/$ACCOUNT",
      "$ACCOUNT",
      "//$LOAD_BALANCER",
      "/       /$LOAD_BALANCER",
      "/$INVALID_MARATHON_PART/$LOAD_BALANCER",
      "$INVALID_MARATHON_PART/$LOAD_BALANCER",
      "/$ACCOUNT/$INVALID_MARATHON_PART",
      "$ACCOUNT/$INVALID_MARATHON_PART",
      "/$ACCOUNT/",
      "$ACCOUNT/",
      "/$ACCOUNT/      ",
      "$ACCOUNT/      ",
      "/$ACCOUNT//$LOAD_BALANCER",
      "$ACCOUNT//$LOAD_BALANCER",
      "/$ACCOUNT/      /$LOAD_BALANCER",
      "$ACCOUNT/      /$LOAD_BALANCER",
      "/$ACCOUNT/$INVALID_MARATHON_PART/$LOAD_BALANCER",
      "$ACCOUNT/$INVALID_MARATHON_PART/$LOAD_BALANCER",
      "/$INVALID_ACCOUNT/$REGION/$LOAD_BALANCER",
      "$INVALID_ACCOUNT/$REGION/$LOAD_BALANCER"
    ]

  }

  @Unroll
  void "static factory method should return an empty optional if either account (#account)/loadBalancerName (#loadBalancerName) are invalid"() {
    expect:
    def dcosPath = DcosSpinnakerLbId.fromVerbose(account, loadBalancerName)
    dcosPath == Optional.empty()

    where:
    account               | loadBalancerName
    null                  | LOAD_BALANCER
    ""                    | LOAD_BALANCER
    "         "           | LOAD_BALANCER
    INVALID_MARATHON_PART | LOAD_BALANCER
    ACCOUNT               | null
    ACCOUNT               | ""
    ACCOUNT               | "         "
    ACCOUNT               | INVALID_MARATHON_PART
  }

  @Unroll
  void "the account (#expectedAccount), lb name (#expectedLoadBalancerName), and haproxy group (#expectedHaproxyGroup) should be correctly parsed when given a valid marathon path \"#path\""() {
    expect:
    def dcosPath = DcosSpinnakerLbId.parseVerbose(path, ACCOUNT).get()
    dcosPath.account == expectedAccount
    dcosPath.loadBalancerName == expectedLoadBalancerName
    dcosPath.loadBalancerHaproxyGroup == expectedHaproxyGroup

    where:
    path                       || expectedAccount || expectedLoadBalancerName || expectedHaproxyGroup
    "$ACCOUNT/$LOAD_BALANCER"  || ACCOUNT         || LOAD_BALANCER            || "${ACCOUNT}_$LOAD_BALANCER".toString()
    "/$ACCOUNT/$LOAD_BALANCER" || ACCOUNT         || LOAD_BALANCER            || "${ACCOUNT}_$LOAD_BALANCER".toString()
  }

  @Unroll
  void "the account (#expectedAccount), lb name (#expectedLoadBalancerName), and haproxy group (#expectedHaproxyGroup) should be correctly parsed when given a valid account and loadBalancerName"() {
    expect:
    def dcosPath = DcosSpinnakerLbId.fromVerbose(ACCOUNT, LOAD_BALANCER).get()
    dcosPath.account == expectedAccount
    dcosPath.loadBalancerName == expectedLoadBalancerName
    dcosPath.loadBalancerHaproxyGroup == expectedHaproxyGroup

    where:
    expectedAccount || expectedLoadBalancerName || expectedHaproxyGroup
    ACCOUNT         || LOAD_BALANCER            || "${ACCOUNT}_$LOAD_BALANCER".toString()
    ACCOUNT         || LOAD_BALANCER            || "${ACCOUNT}_$LOAD_BALANCER".toString()
  }
}
