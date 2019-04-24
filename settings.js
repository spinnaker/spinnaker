'use strict';

var apiHost = process.env.API_HOST || 'http://localhost:8084';
var artifactsEnabled = process.env.ARTIFACTS_ENABLED === 'true';
var artifactsRewriteEnabled = process.env.ARTIFACTS_REWRITE_ENABLED === 'true';
var atlasWebComponentsUrl = process.env.ATLAS_WEB_COMPONENTS_URL;
var authEndpoint = process.env.AUTH_ENDPOINT || apiHost + '/auth/user';
var authEnabled = process.env.AUTH_ENABLED === 'false' ? false : true;
var bakeryDetailUrl =
  process.env.BAKERY_DETAIL_URL || apiHost + '/bakery/logs/{{context.region}}/{{context.status.resourceId}}';
var canaryAccount = process.env.CANARY_ACCOUNT || '';
var canaryEnabled = process.env.CANARY_ENABLED === 'true';
var canaryFeatureDisabled = process.env.CANARY_FEATURE_ENABLED !== 'true';
var canaryStagesEnabled = process.env.CANARY_STAGES_ENABLED === 'true';
var chaosEnabled = process.env.CHAOS_ENABLED === 'true' ? true : false;
var debugEnabled = process.env.DEBUG_ENABLED === 'false' ? false : true;
var defaultMetricStore = process.env.METRIC_STORE || 'atlas';
var displayTimestampsInUserLocalTime = process.env.DISPLAY_TIMESTAMPS_IN_USER_LOCAL_TIME === 'true';
var dryRunEnabled = process.env.DRYRUN_ENABLED === 'true' ? true : false;
var entityTagsEnabled = process.env.ENTITY_TAGS_ENABLED === 'true' ? true : false;
var fiatEnabled = process.env.FIAT_ENABLED === 'true' ? true : false;
var gremlinEnabled = process.env.GREMLIN_ENABLED === 'false' ? false : true;
var iapRefresherEnabled = process.env.IAP_REFRESHER_ENABLED === 'true' ? true : false;
var infrastructureEnabled = process.env.INFRA_ENABLED === 'true' ? true : false;
var managedPipelineTemplatesV2UIEnabled = process.env.MANAGED_PIPELINE_TEMPLATES_V2_UI_ENABLED === 'true';
var managedServiceAccountsEnabled = process.env.MANAGED_SERVICE_ACCOUNTS_ENABLED === 'true';
var onDemandClusterThreshold = process.env.ON_DEMAND_CLUSTER_THRESHOLD || '350';
var reduxLoggerEnabled = process.env.REDUX_LOGGER === 'true';
var templatesEnabled = process.env.TEMPLATES_ENABLED === 'true';
var useClassicFirewallLabels = process.env.USE_CLASSIC_FIREWALL_LABELS === 'true';

window.spinnakerSettings = {
  authEnabled: authEnabled,
  authEndpoint: authEndpoint,
  authTtl: 600000,
  bakeryDetailUrl: bakeryDetailUrl,
  canary: {
    atlasWebComponentsUrl: atlasWebComponentsUrl,
    defaultJudge: 'NetflixACAJudge-v1.0',
    featureDisabled: canaryFeatureDisabled,
    metricsAccountName: canaryAccount,
    metricStore: defaultMetricStore,
    reduxLogger: reduxLoggerEnabled,
    showAllConfigs: true,
    stagesEnabled: canaryStagesEnabled,
    storageAccountName: canaryAccount,
    templatesEnabled: templatesEnabled,
  },
  checkForUpdates: true,
  debugEnabled: debugEnabled,
  defaultCategory: 'serverGroup',
  defaultInstancePort: 80,
  defaultProviders: ['appengine', 'aws', 'azure', 'cloudfoundry', 'dcos', 'ecs', 'gce', 'kubernetes', 'oracle'],
  defaultTimeZone: process.env.TIMEZONE || 'America/Los_Angeles', // see http://momentjs.com/timezone/docs/#/data-utilities/
  feature: {
    artifacts: artifactsEnabled,
    artifactsRewrite: artifactsRewriteEnabled,
    canary: canaryEnabled,
    chaosMonkey: chaosEnabled,
    displayTimestampsInUserLocalTime: displayTimestampsInUserLocalTime,
    dryRunEnabled: dryRunEnabled,
    entityTags: entityTagsEnabled,
    fiatEnabled: fiatEnabled,
    gremlinEnabled: gremlinEnabled,
    iapRefresherEnabled: iapRefresherEnabled,
    // whether stages affecting infrastructure (like "Create Load Balancer") should be enabled or not
    infrastructureStages: infrastructureEnabled,
    jobs: false,
    managedPipelineTemplatesV2UI: managedPipelineTemplatesV2UIEnabled,
    managedServiceAccounts: managedServiceAccountsEnabled,
    notifications: false,
    pagerDuty: false,
    pipelineTemplates: false,
    pipelines: true,
    quietPeriod: false,
    roscoMode: false,
    snapshots: false,
    travis: false,
    versionedProviders: true,
    wercker: false,
  },
  gateUrl: apiHost,
  gitSources: ['stash', 'github', 'bitbucket', 'gitlab'],
  maxPipelineAgeDays: 14,
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
    githubStatus: {
      enabled: true,
    },
    googlechat: {
      enabled: true,
    },
    hipchat: {
      botName: 'Skynet T-800',
      enabled: true,
    },
    pubsub: {
      enabled: true,
    },
    slack: {
      botName: 'spinnakerbot',
      enabled: true,
    },
    sms: {
      enabled: true,
    },
  },
  onDemandClusterThreshold: Number(onDemandClusterThreshold),
  pollSchedule: 30000,
  providers: {
    appengine: {
      defaults: {
        account: 'my-appengine-account',
      },
    },
    aws: {
      defaults: {
        account: 'test',
        region: 'us-east-1',
        iamRole: 'BaseIAMRole',
      },
      defaultSecurityGroups: [],
      loadBalancers: {
        disableManualOidcDialog: false,
        // if true, VPC load balancers will be created as internal load balancers if the selected subnet has a purpose
        // tag that starts with "internal"
        inferInternalFlagFromSubnet: false,
      },
      useAmiBlockDeviceMappings: false,
    },
    azure: {
      defaults: {
        account: 'azure-test',
        region: 'westus',
      },
    },
    cloudfoundry: {
      defaults: {
        account: 'my-cloudfoundry-account',
      },
    },
    dcos: {
      defaults: {
        account: 'my-dcos-account',
      },
    },
    ecs: {
      defaults: {
        account: 'test',
        iamRole: 'BaseIAMRole',
        region: 'us-east-1',
      },
      defaultSecurityGroups: [],
    },
    gce: {
      associatePublicIpAddress: true,
      defaults: {
        account: 'my-google-account',
        instanceTypeStorage: {
          count: 1,
          defaultSettings: {
            disks: [
              {
                sizeGb: 10,
                type: 'pd-ssd',
              },
            ],
          },
          localSSDSupported: false,
          size: 10,
        },
        region: 'us-central1',
        zone: 'us-central1-f',
      },
    },
    kubernetes: {
      defaults: {
        account: 'my-kubernetes-account',
        apiPrefix: 'api/v1/proxy/namespaces/kube-system/services/kubernetes-dashboard/#',
        instanceLinkTemplate: '{{host}}/api/v1/proxy/namespaces/{{namespace}}/pods/{{name}}',
        internalDNSNameTemplate: '{{name}}.{{namespace}}.svc.cluster.local',
        namespace: 'default',
        proxy: 'localhost:8001',
      },
    },
    oracle: {
      defaults: {
        account: 'DEFAULT',
        bakeryRegions: ['us-phoenix-1'],
        region: 'us-phoenix-1',
      },
    },
    titus: {
      defaults: {
        account: 'titustestvpc',
        iamProfile: '{{application}}InstanceProfile',
        region: 'us-east-1',
      },
    },
  },
  pagerDuty: {
    required: false,
  },
  pubsubProviders: ['google'], // TODO(joonlim): Add amazon once it is confirmed that amazon pub/sub works.
  searchVersion: 1,
  triggerTypes: [
    'artifactory',
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
  useClassicFirewallLabels: useClassicFirewallLabels,
  whatsNew: {
    fileName: 'news.md',
    gistId: '32526cd608db3d811b38',
  },
};
