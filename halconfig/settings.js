'use strict';

var gateHost = '{%gate.baseUrl%}';
var bakeryDetailUrl = gateHost + '/bakery/logs/{{context.region}}/{{context.status.resourceId}}';
var authEndpoint = gateHost + '/auth/user';
var authEnabled = '{%features.auth%}' === 'true';
var chaosEnabled = '{%features.chaos%}' === 'true';
var fiatEnabled = '{%features.fiat%}' === 'true';
var iapRefresherEnabled = '{%features.iapRefresherEnabled}' === 'true';
var jobsEnabled = '{%features.jobs%}' === 'true';
var infrastructureStagesEnabled = '{%features.infrastructureStages%}' === 'true';
var pipelineTemplatesEnabled = '{%features.pipelineTemplates%}' === 'true';
var artifactsEnabled = '{%features.artifacts%}' === 'true';
var travisEnabled = '{%features.travis%}' === 'true';
var werckerEnabled = '{%features.wercker%}' === 'true';
var mineCanaryEnabled = '{%features.mineCanary%}' === 'true';
var reduxLoggerEnabled = '{%canary.reduxLogger%}' === 'true';
var defaultMetricsAccountName = '{%canary.defaultMetricsAccount%}';
var defaultStorageAccountName = '{%canary.defaultStorageAccount%}';
var defaultCanaryJudge = '{%canary.defaultJudge%}';
var defaultMetricsStore = '{%canary.defaultMetricsStore%}';
var canaryStagesEnabled = '{%canary.stages%}' === 'true';
var atlasWebComponentsUrl = '{%canary.atlasWebComponentsUrl%}';
var templatesEnabled = '{%canary.templatesEnabled%}' === 'true';
var showAllConfigsEnabled = '{%canary.showAllCanaryConfigs%}' === 'true';
var canaryFeatureDisabled = '{%canary.featureEnabled%}' !== 'true';
var maxPipelineAgeDays = '{%maxPipelineAgeDays%}';
var timezone = '{%timezone%}';
var version = '{%version%}';
var changelogGistId = '{%changelog.gist.id%}';
var changelogGistName = '{%changelog.gist.name%}';
var appengineContainerImageUrlDeploymentsEnabled = '{%features.appengineContainerImageUrlDeployments%}' === 'true';
var gce = {
  defaults: {
    account: '{%google.default.account%}',
    region: '{%google.default.region%}',
    zone: '{%google.default.zone%}',
  },
  associatePublicIpAddress: true,
};
var kubernetes = {
  defaults: {
    account: '{%kubernetes.default.account%}',
    namespace: '{%kubernetes.default.namespace%}',
    proxy: '{%kubernetes.default.proxy%}',
    internalDNSNameTemplate: '{{name}}.{{namespace}}.svc.cluster.local',
    instanceLinkTemplate: '{{host}}/api/v1/proxy/namespaces/{{namespace}}/pods/{{name}}',
  },
};
var appengine = {
  defaults: {
    account: '{%appengine.default.account%}',
    editLoadBalancerStageEnabled: '{%appengine.enabled%}' === 'true',
    containerImageUrlDeployments: appengineContainerImageUrlDeploymentsEnabled,
  },
};
var openstack = {
  defaults: {
    account: '{%openstack.default.account%}',
    region: '{%openstack.default.region%}',
  },
};
var azure = {
  defaults: {
    account: '{%azure.default.account%}',
    region: '{%azure.default.region%}',
  },
};
var oracle = {
  defaults: {
    account: '{%oracle.default.account%}',
    region: '{%oracle.default.region%}',
  },
};
var dcos = {
  defaults: {
    account: '{%dcos.default.account%}',
  },
};
var aws = {
  defaults: {
    account: '{%aws.default.account%}',
    region: '{%aws.default.region%}',
    iamRole: 'BaseIAMRole',
  },
  defaultSecurityGroups: [],
  loadBalancers: {
    // if true, VPC load balancers will be created as internal load balancers if the selected subnet has a purpose
    // tag that starts with "internal"
    inferInternalFlagFromSubnet: false,
  },
  useAmiBlockDeviceMappings: false,
};
var ecs = {
  defaults: {
    account: '{%ecs.default.account%}',
  },
};
var entityTagsEnabled = false;
var netflixMode = false;
var notificationsEnabled = '{%notifications.enabled%}' === 'true';
var slack = {
  enabled: '{%notifications.slack.enabled%}' === 'true',
  botName: '{%notifications.slack.botName%}',
};

window.spinnakerSettings = {
  version: version,
  checkForUpdates: false,
  defaultProviders: [
    'aws',
    'ecs',
    'gce',
    'azure',
    'cloudfoundry',
    'kubernetes',
    'titus',
    'openstack',
    'oracle',
    'dcos',
  ],
  gateUrl: gateHost,
  bakeryDetailUrl: bakeryDetailUrl,
  authEndpoint: authEndpoint,
  pollSchedule: 30000,
  defaultTimeZone: timezone, // see http://momentjs.com/timezone/docs/#/data-utilities/
  defaultCategory: 'serverGroup',
  defaultInstancePort: 80,
  maxPipelineAgeDays: maxPipelineAgeDays,
  providers: {
    azure: azure,
    aws: aws,
    ecs: ecs,
    gce: gce,
    titus: {
      defaults: {
        account: 'titustestvpc',
        region: 'us-east-1',
        iamProfile: '{{application}}InstanceProfile',
      },
    },
    openstack: openstack,
    kubernetes: kubernetes,
    appengine: appengine,
    oracle: oracle,
    dcos: dcos,
  },
  changelog: {
    gistId: changelogGistId,
    fileName: changelogGistName,
  },
  notifications: {
    email: {
      enabled: true,
    },
    hipchat: {
      enabled: true,
      botName: 'Skynet T-800',
    },
    sms: {
      enabled: true,
    },
    slack: slack,
  },
  pagerDuty: {
    required: false,
  },
  authEnabled: authEnabled,
  authTtl: 600000,
  gitSources: ['stash', 'github', 'bitbucket', 'gitlab'],
  pubsubProviders: ['google'], // TODO(joonlim): Add amazon once it is confirmed that amazon pub/sub works.
  triggerTypes: ['git', 'pipeline', 'docker', 'cron', 'jenkins', 'wercker', 'travis', 'pubsub', 'webhook'],
  canary: {
    reduxLogger: reduxLoggerEnabled,
    metricsAccountName: defaultMetricsAccountName,
    storageAccountName: defaultStorageAccountName,
    defaultJudge: defaultCanaryJudge,
    metricStore: defaultMetricsStore,
    stagesEnabled: canaryStagesEnabled,
    atlasWebComponentsUrl: atlasWebComponentsUrl,
    templatesEnabled: templatesEnabled,
    showAllConfigs: showAllConfigsEnabled,
    featureDisabled: canaryFeatureDisabled,
  },
  feature: {
    entityTags: entityTagsEnabled,
    fiatEnabled: fiatEnabled,
    iapRefresherEnabled: iapRefresherEnabled,
    netflixMode: netflixMode,
    chaosMonkey: chaosEnabled,
    jobs: jobsEnabled,
    pipelineTemplates: pipelineTemplatesEnabled,
    notifications: notificationsEnabled,
    artifacts: artifactsEnabled,
    canary: mineCanaryEnabled,
    infrastructureStages: infrastructureStagesEnabled,
    pipelines: true,
    fastProperty: true,
    vpcMigrator: true,
    pagerDuty: false,
    clusterDiff: false,
    roscoMode: true,
    snapshots: false,
    travis: travisEnabled,
    wercker: werckerEnabled,
    versionedProviders: true,
  },
};
