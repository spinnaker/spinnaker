'use strict';

var gateHost = '{%gate.baseUrl%}';
var atlasWebComponentsUrl = '{%canary.atlasWebComponentsUrl%}';
var authEnabled = '{%features.auth%}' === 'true';
var authEndpoint = gateHost + '/auth/user';
var bakeryDetailUrl = gateHost + '/bakery/logs/{{context.region}}/{{context.status.resourceId}}';
var canaryFeatureDisabled = '{%canary.featureEnabled%}' !== 'true';
var canaryStagesEnabled = '{%canary.stages%}' === 'true';
var chaosEnabled = '{%features.chaos%}' === 'true';
var defaultCanaryJudge = '{%canary.defaultJudge%}';
var defaultMetricsStore = '{%canary.defaultMetricsStore%}';
var defaultMetricsAccountName = '{%canary.defaultMetricsAccount%}';
var defaultStorageAccountName = '{%canary.defaultStorageAccount%}';
var fiatEnabled = '{%features.fiat%}' === 'true';
var mineCanaryEnabled = '{%features.mineCanary%}' === 'true';
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
var timezone = '{%timezone%}';
var version = '{%version%}';

// Cloud Providers
var appengine = {
  defaults: {
    account: '{%appengine.default.account%}',
  },
};
var oracle = {
  defaults: {
    account: '{%oracle.default.account%}',
    bakeryRegions: '{%oracle.default.bakeryRegions%}',
    region: '{%oracle.default.region%}',
  },
};
var aws = {
  defaults: {
    account: '{%aws.default.account%}',
    region: '{%aws.default.region%}',
  },
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
};
var huaweicloud = {
  defaults: {
    account: '{%huaweicloud.default.account%}',
    region: '{%huaweicloud.default.region%}',
  },
};
var tencentcloud = {
  defaults: {
    account: '{%tencentcloud.default.account%}',
    region: '{%tencentcloud.default.region%}',
  },
};

window.spinnakerSettings = {
  authEnabled: authEnabled,
  authEndpoint: authEndpoint,
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
  defaultInstancePort: 80,
  defaultTimeZone: timezone, // see http://momentjs.com/timezone/docs/#/data-utilities/
  feature: {
    canary: mineCanaryEnabled,
    chaosMonkey: chaosEnabled,
    fiatEnabled: fiatEnabled,
    pipelineTemplates: pipelineTemplatesEnabled,
    roscoMode: true,
  },
  gateUrl: gateHost,
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
    microsoftteams: {
      enabled: true,
    },
    pubsub: {
      enabled: true,
    },
    slack: slack,
    sms: sms,
  },
  providers: {
    appengine: appengine,
    aws: aws,
    azure: azure,
    cloudfoundry: cloudfoundry,
    dcos: dcos,
    ecs: ecs,
    gce: gce,
    huaweicloud: huaweicloud,
    kubernetes: {},
    oracle: oracle,
    tencentcloud: tencentcloud,
  },
  version: version,
};
