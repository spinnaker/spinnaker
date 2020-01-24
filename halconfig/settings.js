'use strict';

var gateHost = '{%gate.baseUrl%}';
var artifactsEnabled = '{%features.artifacts%}' === 'true';
var artifactsRewriteEnabled = '{%features.artifactsRewrite%}' === 'true';
var atlasWebComponentsUrl = '{%canary.atlasWebComponentsUrl%}';
var authEnabled = '{%features.auth%}' === 'true';
var authEndpoint = gateHost + '/auth/user';
var bakeryDetailUrl = gateHost + '/bakery/logs/{{context.region}}/{{context.status.resourceId}}';
var canaryFeatureDisabled = '{%canary.featureEnabled%}' !== 'true';
var canaryStagesEnabled = '{%canary.stages%}' === 'true';
var changelogGistId = '{%changelog.gist.id%}';
var changelogGistName = '{%changelog.gist.name%}';
var chaosEnabled = '{%features.chaos%}' === 'true';
var defaultCanaryJudge = '{%canary.defaultJudge%}';
var defaultMetricsStore = '{%canary.defaultMetricsStore%}';
var defaultMetricsAccountName = '{%canary.defaultMetricsAccount%}';
var defaultStorageAccountName = '{%canary.defaultStorageAccount%}';
var displayTimestampsInUserLocalTime = '{%features.displayTimestampsInUserLocalTime%}' === 'true';
var entityTagsEnabled = false;
var fiatEnabled = '{%features.fiat%}' === 'true';
var gceScaleDownControlsEnabled = '{%features.gceScaleDownControlsEnabled%}' === 'true';
var gceStatefulMigsEnabled = '{%features.gceStatefulMigsEnabled%}' === 'true';
var gremlinEnabled = '{%features.gremlin%}' === 'true';
var iapRefresherEnabled = '{%features.iapRefresherEnabled%}' === 'true';
var infrastructureStagesEnabled = '{%features.infrastructureStages%}' === 'true';
var maxPipelineAgeDays = '{%maxPipelineAgeDays%}';
var mineCanaryEnabled = '{%features.mineCanary%}' === 'true';
var notificationsEnabled = '{%notifications.enabled%}' === 'true';
var onDemandClusterThreshold = '{%onDemandClusterThreshold%}';
var pipelineTemplatesEnabled = '{%features.pipelineTemplates%}' === 'true';
var reduxLoggerEnabled = '{%canary.reduxLogger%}' === 'true';
var showAllConfigsEnabled = '{%canary.showAllCanaryConfigs%}' === 'true';
var slack = {
  botName: '{%notifications.slack.botName%}',
  enabled: '{%notifications.slack.enabled%}' === 'true',
};
var sms = {
  enabled: '{%notifications.twilio.enabled%}' === 'true',
};
var githubStatus = {
  enabled: '{%notifications.github-status.enabled%}' === 'true',
};
var templatesEnabled = '{%canary.templatesEnabled%}' === 'true';
var travisEnabled = '{%features.travis%}' === 'true';
var timezone = '{%timezone%}';
var version = '{%version%}';
var werckerEnabled = '{%features.wercker%}' === 'true';
var functionsEnabled = '{%features.functions%}' === 'true';

// Cloud Providers
var appengine = {
  defaults: {
    account: '{%appengine.default.account%}',
    editLoadBalancerStageEnabled: '{%appengine.enabled%}' === 'true',
  },
};
var aws = {
  defaults: {
    account: '{%aws.default.account%}',
    iamRole: 'BaseIAMRole',
    region: '{%aws.default.region%}',
  },
  defaultSecurityGroups: [],
  loadBalancers: {
    // if true, VPC load balancers will be created as internal load balancers if the selected subnet has a purpose
    // tag that starts with "internal"
    inferInternalFlagFromSubnet: false,
  },
  useAmiBlockDeviceMappings: false,
};
var azure = {
  defaults: {
    account: '{%azure.default.account%}',
    region: '{%azure.default.region%}',
  },
};
var cloudfoundry = {
  defaults: {
    account: '{%cloudfoundry.default.account%}',
  },
};
var dcos = {
  defaults: {
    account: '{%dcos.default.account%}',
  },
};
var ecs = {
  defaults: {
    account: '{%ecs.default.account%}',
  },
};
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
    instanceLinkTemplate: '{{host}}/api/v1/proxy/namespaces/{{namespace}}/pods/{{name}}',
    internalDNSNameTemplate: '{{name}}.{{namespace}}.svc.cluster.local',
    namespace: '{%kubernetes.default.namespace%}',
    proxy: '{%kubernetes.default.proxy%}',
  },
};
var huaweicloud = {
  defaults: {
    account: '{%huaweicloud.default.account%}',
    region: '{%huaweicloud.default.region%}',
  },
};
var oracle = {
  defaults: {
    account: '{%oracle.default.account%}',
    region: '{%oracle.default.region%}',
  },
};

window.spinnakerSettings = {
  authEnabled: authEnabled,
  authEndpoint: authEndpoint,
  authTtl: 600000,
  bakeryDetailUrl: bakeryDetailUrl,
  canary: {
    atlasWebComponentsUrl: atlasWebComponentsUrl,
    defaultJudge: defaultCanaryJudge,
    featureDisabled: canaryFeatureDisabled,
    reduxLogger: reduxLoggerEnabled,
    metricsAccountName: defaultMetricsAccountName,
    metricStore: defaultMetricsStore,
    showAllConfigs: showAllConfigsEnabled,
    stagesEnabled: canaryStagesEnabled,
    storageAccountName: defaultStorageAccountName,
    templatesEnabled: templatesEnabled,
  },
  changelog: {
    fileName: changelogGistName,
    gistId: changelogGistId,
  },
  checkForUpdates: false,
  defaultInstancePort: 80,
  defaultProviders: [
    'appengine',
    'aws',
    'azure',
    'cloudfoundry',
    'dcos',
    'ecs',
    'gce',
    'huaweicloud',
    'kubernetes',
    'oracle',
    'titus',
  ],
  defaultTimeZone: timezone, // see http://momentjs.com/timezone/docs/#/data-utilities/
  feature: {
    artifacts: artifactsEnabled,
    artifactsRewrite: artifactsRewriteEnabled,
    canary: mineCanaryEnabled,
    chaosMonkey: chaosEnabled,
    displayTimestampsInUserLocalTime: displayTimestampsInUserLocalTime,
    entityTags: entityTagsEnabled,
    fiatEnabled: fiatEnabled,
    gceScaleDownControlsEnabled: gceScaleDownControlsEnabled,
    gceStatefulMigsEnabled: gceStatefulMigsEnabled,
    gremlinEnabled: gremlinEnabled,
    iapRefresherEnabled: iapRefresherEnabled,
    infrastructureStages: infrastructureStagesEnabled,
    notifications: notificationsEnabled,
    pagerDuty: false,
    pipelines: true,
    pipelineTemplates: pipelineTemplatesEnabled,
    roscoMode: true,
    slack: false,
    snapshots: false,
    travis: travisEnabled,
    versionedProviders: true,
    wercker: werckerEnabled,
    functions: functionsEnabled,
  },
  gateUrl: gateHost,
  gitSources: ['bitbucket', 'gitlab', 'github', 'stash'],
  maxPipelineAgeDays: maxPipelineAgeDays,
  newApplicationDefaults: {
    chaosMonkey: false,
  },
  notifications: {
    bearychat: {
      enabled: true,
    },
    email: {
      enabled: true,
    },
    githubStatus: githubStatus,
    googlechat: {
      enabled: true,
    },
    pubsub: {
      enabled: true,
    },
    slack: slack,
    sms: sms,
  },
  onDemandClusterThreshold: onDemandClusterThreshold,
  pagerDuty: {
    required: false,
  },
  pollSchedule: 30000,
  providers: {
    appengine: appengine,
    aws: aws,
    azure: azure,
    cloudfoundry: cloudfoundry,
    dcos: dcos,
    ecs: ecs,
    gce: gce,
    huaweicloud: huaweicloud,
    kubernetes: kubernetes,
    oracle: oracle,
    titus: {
      defaults: {
        account: 'titustestvpc',
        iamProfile: '{{application}}InstanceProfile',
        region: 'us-east-1',
      },
    },
  },
  pubsubProviders: ['google'], // TODO(joonlim): Add amazon once it is confirmed that amazon pub/sub works.
  triggerTypes: [
    'artifactory',
    'nexus',
    'concourse',
    'cron',
    'docker',
    'git',
    'jenkins',
    'pipeline',
    'pubsub',
    'travis',
    'webhook',
    'wercker',
  ],
  version: version,
};
