package com.netflix.spinnaker.clouddriver.dcos.model

import com.netflix.spinnaker.clouddriver.model.HealthState
import mesosphere.marathon.client.model.v2.App
import mesosphere.marathon.client.model.v2.AppVersionInfo
import mesosphere.marathon.client.model.v2.PortDefinition
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class DcosLoadBalancerSpec extends Specification {

  static final private String APP = "testapp"
  private static final String ACCOUNT = "testaccount"
  private static final String REGION = "us-west-1"
  private static final String LB_NAME = "${APP}-frontend"

  void "region should be set to 'global' if the load balancer exists directly under the account group"() {
    setup:
    def lbApp = createLbApp("/${ACCOUNT}/${LB_NAME}")

    when:
    @Subject def lb = new DcosLoadBalancer(ACCOUNT, REGION, lbApp, [])

    then:
    lb.region == REGION
  }

  void "created time is set if available"() {
    setup:
    def currentInstant = Instant.now()
    def lbApp = createLbApp("/${ACCOUNT}/${LB_NAME}")
    lbApp.getVersionInfo() >> new AppVersionInfo(lastConfigChangeAt: currentInstant)

    when:
    @Subject def lb = new DcosLoadBalancer(ACCOUNT, REGION, lbApp, [])

    then:
    lb.createdTime == currentInstant.toEpochMilli()
  }

  void "server group and instance information is populated if provided"() {
    setup:
    def lbApp = createLbApp("/${ACCOUNT}/${LB_NAME}")

    def instance = Stub(DcosInstance) {
      getName() >> "taskName"
      getZone() >> ACCOUNT
      getHealthState() >> HealthState.Up
    }

    def serverGroup = Stub(DcosServerGroup) {
      getName() >> "${APP}-v000"
      getRegion() >> REGION
      getAccount() >> ACCOUNT
      getInstances() >> [instance]
    }

    def serverGroups = [serverGroup]

    when:
    @Subject def lb = new DcosLoadBalancer(ACCOUNT, REGION, lbApp, serverGroups)

    then:
    lb.serverGroups.size() == 1
    lb.serverGroups[0].name == "${APP}-v000".toString()
    lb.serverGroups[0].region == REGION
    lb.serverGroups[0].account == ACCOUNT

    lb.serverGroups[0].instances.size() == 1
    lb.serverGroups[0].instances[0].id == 'taskName'
    lb.serverGroups[0].instances[0].zone == ACCOUNT
    lb.serverGroups[0].instances[0].health.state == "Up"
  }

  void "bindHttpHttps is set to true on the description when ports 80 and 443 exist in the port definitions"() {
    setup:
    def lbApp = createLbApp("/${ACCOUNT}/${LB_NAME}")
    lbApp.getPortDefinitions() >> [new PortDefinition(port:80, protocol: 'tcp'),
                                   new PortDefinition(port:443, protocol: 'tcp')]

    when:
    @Subject def lb = new DcosLoadBalancer(ACCOUNT, REGION, lbApp, [])

    then:
    lb.description.bindHttpHttps
  }

  void "port range is created correctly from the port definitions"() {
    setup:
    def lbApp = createLbApp("/${ACCOUNT}/${LB_NAME}")
    lbApp.getPortDefinitions() >> [new PortDefinition(port:100, protocol: 'tcp'),
                                   new PortDefinition(port:101, protocol: 'tcp'),
                                   new PortDefinition(port:102, protocol: 'tcp')]


    when:
    @Subject def lb = new DcosLoadBalancer(ACCOUNT, REGION, lbApp, [])

    then:
    lb.description.portRange.minPort == 100
    lb.description.portRange.maxPort == 102
    lb.description.portRange.protocol == 'tcp'
  }

  def createLbApp(id) {
    Stub(App) {
      getId() >> id
    }
  }
}
