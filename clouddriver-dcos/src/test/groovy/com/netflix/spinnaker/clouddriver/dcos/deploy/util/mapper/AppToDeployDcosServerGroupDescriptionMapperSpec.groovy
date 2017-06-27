package com.netflix.spinnaker.clouddriver.dcos.deploy.util.mapper

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DeployDcosServerGroupDescription
import mesosphere.marathon.client.model.v2.App
import mesosphere.marathon.client.model.v2.Command
import mesosphere.marathon.client.model.v2.Container
import mesosphere.marathon.client.model.v2.Docker
import mesosphere.marathon.client.model.v2.ExternalVolume
import mesosphere.marathon.client.model.v2.Fetchable
import mesosphere.marathon.client.model.v2.HealthCheck
import mesosphere.marathon.client.model.v2.LocalVolume
import mesosphere.marathon.client.model.v2.Parameter
import mesosphere.marathon.client.model.v2.PersistentLocalVolume
import mesosphere.marathon.client.model.v2.PortDefinition
import mesosphere.marathon.client.model.v2.PortMapping
import mesosphere.marathon.client.model.v2.ReadinessCheck
import mesosphere.marathon.client.model.v2.Residency
import mesosphere.marathon.client.model.v2.UpgradeStrategy
import spock.lang.Specification

class AppToDeployDcosServerGroupDescriptionMapperSpec extends Specification {
  private static final ACCOUNT = 'account'
  private static final CLUSTER = 'default'
  private static final GROUP = 'sub'
  private static final REGION = "${CLUSTER}/${GROUP}".toString()

  private static final APP_NAME = 'app-dev-feat1-v000'

  void 'AppToDeployDcosServerGroupDescriptionMapper should map simple fields from the marathon application definition to the description'() {
    given:
    App app = new App()

    app.id = "$ACCOUNT/$GROUP/$APP_NAME"

    app.cmd = 'command'
    app.args = ['arg1', 'arg2']
    app.user = 'user'
    app.env = [key: 'value', key2: 'value2']
    app.instances = 2
    app.cpus = 0.5
    app.mem = 64
    app.disk = 100
    app.gpus = 1
    app.constraints = [['rack_id', 'CLUSTER', 'rack-1'], ['hostname', 'UNIQUE']]
    app.fetch = [new Fetchable(uri: 'uri', executable: true, extract: true, cache: true, outputFile: 'file')]
    app.storeUrls = ['url1', 'url2']
    app.backoffSeconds = 12
    app.backoffFactor = 0.3
    app.maxLaunchDelaySeconds = 500
    app.dependencies = ['dep1', 'dep2']
    app.upgradeStrategy = new UpgradeStrategy(maximumOverCapacity: 0.5, minimumHealthCapacity: 0.5)
    app.labels = [lbl1: 'value1', lbl2: 'value2']
    app.acceptedResourceRoles = ['role1', 'role2']
    app.residency = new Residency(taskLostBehaviour: 'relaunch', relaunchEscalationTimeoutSeconds: 10)
    app.taskKillGracePeriodSeconds = 300
    app.secrets = [secret0: [source: 'source1'], secret1: [source: 'source2']]
    app.requirePorts = true

    when:
    def desc = AppToDeployDcosServerGroupDescriptionMapper.map(app, ACCOUNT, CLUSTER)

    then:
    noExceptionThrown()

    desc.application == 'app'
    desc.stack == 'dev'
    desc.freeFormDetails == 'feat1'
    desc.region == REGION
    desc.group == GROUP
    desc.dcosCluster == CLUSTER

    desc.cmd == 'command'
    desc.args == ['arg1', 'arg2']
    desc.dcosUser == 'user'
    desc.env == [key: 'value', key2: 'value2']
    desc.desiredCapacity == 2
    desc.cpus == 0.5d
    desc.mem == 64d
    desc.disk == 100d
    desc.gpus == 1d
    desc.constraints == 'rack_id:CLUSTER:rack-1,hostname:UNIQUE'
    desc.fetch == [new DeployDcosServerGroupDescription.Fetchable(uri: 'uri', executable: true, extract: true, cache: true, outputFile: 'file')]
    desc.storeUrls == ['url1', 'url2']
    desc.backoffSeconds == 12
    desc.backoffFactor == 0.3d
    desc.maxLaunchDelaySeconds == 500
    desc.dependencies == ['dep1', 'dep2']
    desc.upgradeStrategy == new DeployDcosServerGroupDescription.UpgradeStrategy(maximumOverCapacity: 0.5, minimumHealthCapacity: 0.5)
    desc.labels == [lbl1: 'value1', lbl2: 'value2']
    desc.acceptedResourceRoles == ['role1', 'role2']
    desc.residency == new DeployDcosServerGroupDescription.Residency(taskLostBehaviour: 'relaunch', relaunchEscalationTimeoutSeconds: 10)
    desc.taskKillGracePeriodSeconds == 300
    desc.secrets == [secret0: [source: 'source1'], secret1: [source: 'source2']]
    desc.requirePorts
  }

  void 'AppToDeployDcosServerGroupDescriptionMapper should handle null/undefined fields'() {
    given:
    App app = new App()

    app.id = "$ACCOUNT/$GROUP/$APP_NAME"

    when:
    def desc = AppToDeployDcosServerGroupDescriptionMapper.map(app, ACCOUNT, CLUSTER)

    then:
    noExceptionThrown()

    desc.application == 'app'
    desc.stack == 'dev'
    desc.freeFormDetails == 'feat1'
    desc.region == REGION
    desc.group == GROUP
    desc.dcosCluster == CLUSTER

    !desc.cmd
    !desc.args
    !desc.dcosUser
    !desc.env
    !desc.desiredCapacity

    !desc.cpus
    !desc.mem
    !desc.disk
    !desc.gpus
    !desc.constraints
    !desc.fetch
    !desc.storeUrls
    !desc.backoffSeconds
    !desc.backoffFactor
    !desc.maxLaunchDelaySeconds
    !desc.dependencies
    !desc.upgradeStrategy
    !desc.labels
    !desc.acceptedResourceRoles
    !desc.residency
    !desc.taskKillGracePeriodSeconds
    !desc.secrets
    !desc.requirePorts
  }

  void 'AppToDeployDcosServerGroupDescriptionMapper should handle null container information'() {
    given:
    App app = new App()

    app.id = "$ACCOUNT/$GROUP/$APP_NAME"
    app.container = null

    when:
    def desc = AppToDeployDcosServerGroupDescriptionMapper.map(app, ACCOUNT, CLUSTER)

    then:
    noExceptionThrown()
    desc.docker == null
  }

  void 'AppToDeployDcosServerGroupDescriptionMapper should handle null docker information'() {
    given:
    App app = new App()

    app.id = "$ACCOUNT/$GROUP/$APP_NAME"
    app.container = new Container()

    when:
    def desc = AppToDeployDcosServerGroupDescriptionMapper.map(app, ACCOUNT, CLUSTER)

    then:
    noExceptionThrown()
    desc.docker == null
  }

  void 'AppToDeployDcosServerGroupDescriptionMapper should populate basic docker information'() {
    given:
    App app = new App()

    app.id = "$ACCOUNT/$GROUP/$APP_NAME"
    app.container = new Container(docker: new Docker(image: 'example.hub.com/user/image:latest',
      network: 'HOST',
      forcePullImage: true,
      privileged: true,
      parameters: [new Parameter(key: 'key1', value: 'value1'), new Parameter(key: 'key2', value: 'value2')]))

    when:
    def desc = AppToDeployDcosServerGroupDescriptionMapper.map(app, ACCOUNT, CLUSTER)

    then:
    noExceptionThrown()
    desc.docker.image == new DeployDcosServerGroupDescription.Image(registry: 'example.hub.com',
      repository: 'user/image',
      tag: 'latest',
      imageId: 'example.hub.com/user/image:latest')

    desc.docker.privileged
    desc.docker.forcePullImage
    desc.docker.network == 'HOST'
    desc.docker.parameters == [key1: 'value1', key2: 'value2']

    desc.networkType == 'HOST'
  }

  void 'AppToDeployDcosServerGroupDescriptionMapper handles null docker parameters'() {
    given:
    App app = new App()

    app.id = "$ACCOUNT/$GROUP/$APP_NAME"
    app.container = new Container(docker: new Docker(image: 'example.hub.com/user/image:latest',
      network: "HOST",
      parameters: null))

    when:
    def desc = AppToDeployDcosServerGroupDescriptionMapper.map(app, ACCOUNT, CLUSTER)

    then:
    noExceptionThrown()
    desc.docker.parameters == null
  }

  void 'AppToDeployDcosServerGroupDescriptionMapper maps portMappings to service endpoints, if present'() {
    given:
    App app = new App()

    app.id = "$ACCOUNT/$GROUP/$APP_NAME"
    app.container = new Container(docker: new Docker(image: 'example.hub.com/user/image:latest',
      network: 'BRIDGE',
      portMappings: [new PortMapping(containerPort: 1234,
        protocol: 'tcp',
        name: 'aPort',
        labels: [VIP_0: 'vip'])]
    ))

    when:
    def desc = AppToDeployDcosServerGroupDescriptionMapper.map(app, ACCOUNT, CLUSTER)

    then:
    noExceptionThrown()
    desc.serviceEndpoints == [new DeployDcosServerGroupDescription.ServiceEndpoint(networkType: 'BRIDGE',
      port: 1234,
      protocol: 'tcp',
      loadBalanced: true,
      name: 'aPort',
      labels: [VIP_0: 'vip'],
      exposeToHost: false)]
  }

  void 'AppToDeployDcosServerGroupDescriptionMapper sets exposeToHost if the network type is USER and hostPort is 0'() {
    given:
    App app = new App()

    app.id = "$ACCOUNT/$GROUP/$APP_NAME"
    app.container = new Container(docker: new Docker(image: 'example.hub.com/user/image:latest',
      network: 'USER',
      portMappings: [new PortMapping(containerPort: 1234,
        protocol: 'tcp',
        name: 'aPort',
        labels: [VIP_0: 'vip'],
        hostPort: 0)]
    ))

    when:
    def desc = AppToDeployDcosServerGroupDescriptionMapper.map(app, ACCOUNT, CLUSTER)

    then:
    noExceptionThrown()
    desc.serviceEndpoints == [new DeployDcosServerGroupDescription.ServiceEndpoint(
      networkType: 'USER',
      port: 1234,
      protocol: 'tcp',
      loadBalanced: true,
      name: 'aPort',
      labels: [VIP_0: 'vip'],
      exposeToHost: true)]
  }

  void 'AppToDeployDcosServerGroupDescriptionMapper handles null port mappings labels'() {
    given:
    App app = new App()

    app.id = "$ACCOUNT/$GROUP/$APP_NAME"
    app.container = new Container(docker: new Docker(image: 'example.hub.com/user/image:latest',
      network: 'BRIDGE',
      portMappings: [new PortMapping(containerPort: 1234,
        protocol: 'tcp',
        name: 'aPort',
        labels: null)]
    ))

    when:
    def desc = AppToDeployDcosServerGroupDescriptionMapper.map(app, ACCOUNT, CLUSTER)

    then:
    noExceptionThrown()
    desc.serviceEndpoints == [new DeployDcosServerGroupDescription.ServiceEndpoint(networkType: 'BRIDGE',
      port: 1234,
      protocol: 'tcp',
      loadBalanced: false,
      name: 'aPort',
      labels: null)]
  }

  void 'AppToDeployDcosServerGroupDescriptionMapper maps portDefinitions to service endpoints, if port mappings are not present'() {
    given:
    App app = new App()

    app.id = "$ACCOUNT/$GROUP/$APP_NAME"

    app.portDefinitions = [new PortDefinition(port: 1234,
      protocol: 'tcp',
      name: 'aPort',
      labels: [VIP_0: 'vip'])]

    when:
    def desc = AppToDeployDcosServerGroupDescriptionMapper.map(app, ACCOUNT, CLUSTER)

    then:
    noExceptionThrown()
    desc.serviceEndpoints == [new DeployDcosServerGroupDescription.ServiceEndpoint(networkType: 'HOST',
      port: 1234,
      protocol: 'tcp',
      loadBalanced: true,
      labels: [VIP_0: 'vip'],
      name: 'aPort',
      exposeToHost: false)]
  }

  void 'AppToDeployDcosServerGroupDescriptionMapper handles null portDefinitions'() {
    given:
    App app = new App()

    app.id = "$ACCOUNT/$GROUP/$APP_NAME"
    app.portDefinitions = null

    when:
    def desc = AppToDeployDcosServerGroupDescriptionMapper.map(app, ACCOUNT, CLUSTER)

    then:
    noExceptionThrown()
    desc.serviceEndpoints == []
  }

  void 'AppToDeployDcosServerGroupDescriptionMapper handles null port definition labels'() {
    given:
    App app = new App()

    app.id = "$ACCOUNT/$GROUP/$APP_NAME"
    app.portDefinitions = [new PortDefinition(port: 1234,
      protocol: 'tcp',
      name: 'aPort',
      labels: null)]

    when:
    def desc = AppToDeployDcosServerGroupDescriptionMapper.map(app, ACCOUNT, CLUSTER)

    then:
    noExceptionThrown()
    desc.serviceEndpoints == [new DeployDcosServerGroupDescription.ServiceEndpoint(
      networkType: 'HOST',
      port: 1234,
      protocol: 'tcp',
      loadBalanced: false,
      labels: null,
      name: 'aPort')]
  }

  void 'AppToDeployDcosServerGroupDescriptionMapper maps health checks'() {
    given:
    App app = new App()

    app.id = "$ACCOUNT/$GROUP/$APP_NAME"
    app.healthChecks = [new HealthCheck(protocol: 'tcp',
      path: '/health',
      command: new Command(value: 'nonsense'),
      portIndex: 0,
      port: 80,
      gracePeriodSeconds: 60,
      intervalSeconds: 10,
      timeoutSeconds: 180,
      maxConsecutiveFailures: 3,
      ignoreHttp1xx: true)]

    when:
    def desc = AppToDeployDcosServerGroupDescriptionMapper.map(app, ACCOUNT, CLUSTER)

    then:
    noExceptionThrown()
    desc.healthChecks == [new DeployDcosServerGroupDescription.HealthCheck(protocol: 'tcp',
      path: '/health',
      command: 'nonsense',
      portIndex: 0,
      port: 80,
      gracePeriodSeconds: 60,
      intervalSeconds: 10,
      timeoutSeconds: 180,
      maxConsecutiveFailures: 3,
      ignoreHttp1xx: true
    )]
  }

  void 'AppToDeployDcosServerGroupDescriptionMapper handles a null command in health checks'() {
    given:
    App app = new App()

    app.id = "$ACCOUNT/$GROUP/$APP_NAME"
    app.healthChecks = [new HealthCheck(protocol: 'tcp',
      path: '/health',
      command: null,
      portIndex: 0,
      port: 80,
      gracePeriodSeconds: 60,
      intervalSeconds: 10,
      timeoutSeconds: 180,
      maxConsecutiveFailures: 3,
      ignoreHttp1xx: true)]

    when:
    def desc = AppToDeployDcosServerGroupDescriptionMapper.map(app, ACCOUNT, CLUSTER)

    then:
    noExceptionThrown()
    desc.healthChecks == [new DeployDcosServerGroupDescription.HealthCheck(protocol: 'tcp',
      path: '/health',
      command: null,
      portIndex: 0,
      port: 80,
      gracePeriodSeconds: 60,
      intervalSeconds: 10,
      timeoutSeconds: 180,
      maxConsecutiveFailures: 3,
      ignoreHttp1xx: true
    )]
  }

  void 'AppToDeployDcosServerGroupDescriptionMapper maps readiness checks'() {
    given:
    App app = new App()

    app.id = "$ACCOUNT/$GROUP/$APP_NAME"
    app.readinessChecks = [new ReadinessCheck(
      name: 'avail',
      protocol: 'tcp',
      path: '/available',
      portName: 'port1',
      intervalSeconds: 10,
      timeoutSeconds: 180,
      httpStatusCodesForReady: [200],
      preserveLastResponse: true)]

    when:
    def desc = AppToDeployDcosServerGroupDescriptionMapper.map(app, ACCOUNT, CLUSTER)

    then:
    noExceptionThrown()
    desc.readinessChecks == [new DeployDcosServerGroupDescription.ReadinessCheck(
      name: 'avail',
      protocol: 'tcp',
      path: '/available',
      portName: 'port1',
      intervalSeconds: 10,
      timeoutSeconds: 180,
      httpStatusCodesForReady: [200],
      preserveLastResponse: true)]
  }

  void 'AppToDeployDcosServerGroupDescriptionMapper maps volumes'() {
    given:
    App app = new App()

    app.id = "$ACCOUNT/$GROUP/$APP_NAME"

    def info = new PersistentLocalVolume.PersistentLocalVolumeInfo()
    info.size = 100

    app.container = new Container(volumes: [
      new LocalVolume(
        containerPath: 'local',
        mode: 'RO',
        hostPath: '/'
      ),
      new ExternalVolume(
        containerPath: 'ext',
        mode: 'RW',
        name: 'ext',
        provider: 'dvdi'
      ),
      new PersistentLocalVolume(
        containerPath: 'pers',
        mode: 'RO',
        persistentLocalVolumeInfo: info
      )
    ])

    when:
    def desc = AppToDeployDcosServerGroupDescriptionMapper.map(app, ACCOUNT, CLUSTER)

    then:
    noExceptionThrown()

    desc.dockerVolumes == [new DeployDcosServerGroupDescription.DockerVolume(
      containerPath: 'local',
      hostPath: '/',
      mode: 'RO'
    )]

    desc.externalVolumes == [new DeployDcosServerGroupDescription.ExternalVolume(
      external: new DeployDcosServerGroupDescription.ExternalStorage(name: 'ext', provider: 'dvdi'),
      containerPath: 'ext',
      mode: 'RW'
    )]

    desc.persistentVolumes == [new DeployDcosServerGroupDescription.PersistentVolume(
      persistent: new DeployDcosServerGroupDescription.PersistentStorage(size: 100),
      containerPath: 'pers',
      mode: 'RO'
    )]
  }
}
