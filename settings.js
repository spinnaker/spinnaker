'use strict';

var gateHost = process.env.API_HOST || 'http://localhost:8084';
var bakeryDetailUrl =
  process.env.BAKERY_DETAIL_URL || gateHost + '/bakery/logs/{{context.region}}/{{context.status.resourceId}}';
var authEndpoint = process.env.AUTH_ENDPOINT || gateHost + '/auth/user';
var authEnabled = process.env.AUTH_ENABLED === 'false' ? false : true;
var netflixMode = process.env.NETFLIX_MODE === 'true' ? true : false;
var chaosEnabled = netflixMode || process.env.CHAOS_ENABLED === 'true' ? true : false;
var fiatEnabled = process.env.FIAT_ENABLED === 'true' ? true : false;
var iapRefresherEnabled = process.env.IAP_REFRESHER_ENABLED === 'true' ? true : false;
var entityTagsEnabled = process.env.ENTITY_TAGS_ENABLED === 'true' ? true : false;
var debugEnabled = process.env.DEBUG_ENABLED === 'false' ? false : true;
var canaryEnabled = process.env.CANARY_ENABLED === 'true';
var infrastructureEnabled = process.env.INFRA_ENABLED === 'true' ? true : false;
var dryRunEnabled = process.env.DRYRUN_ENABLED === 'true' ? true : false;
var reduxLoggerEnabled = process.env.REDUX_LOGGER === 'true';
var defaultMetricStore = process.env.METRIC_STORE || 'atlas';
var canaryStagesEnabled = process.env.CANARY_STAGES_ENABLED === 'true';
var templatesEnabled = process.env.TEMPLATES_ENABLED === 'true';
var atlasWebComponentsUrl = process.env.ATLAS_WEB_COMPONENTS_URL;
var canaryAccount = process.env.CANARY_ACCOUNT || '';
var canaryFeatureDisabled = process.env.CANARY_FEATURE_ENABLED !== 'true';
var useClassicFirewallLabels = process.env.USE_CLASSIC_FIREWALL_LABELS === 'true';
var artifactsEnabled = process.env.ARTIFACTS_ENABLED === 'true';
var managedServiceAccountsEnabled = process.env.MANAGED_SERVICE_ACCOUNTS_ENABLED === 'true';

window.spinnakerSettings = {
  checkForUpdates: true,
  debugEnabled: debugEnabled,
  defaultProviders: ['aws', 'gce', 'azure', 'cloudfoundry', 'kubernetes', 'dcos', 'openstack', 'oracle', 'ecs'],
  gateUrl: gateHost,
  bakeryDetailUrl: bakeryDetailUrl,
  authEndpoint: authEndpoint,
  pollSchedule: 30000,
  maxPipelineAgeDays: 14,
  defaultTimeZone: process.env.TIMEZONE || 'America/Los_Angeles', // see http://momentjs.com/timezone/docs/#/data-utilities/
  defaultCategory: 'serverGroup',
  defaultInstancePort: 80,
  providers: {
    azure: {
      defaults: {
        account: 'azure-test',
        region: 'westus',
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
        // if true, VPC load balancers will be created as internal load balancers if the selected subnet has a purpose
        // tag that starts with "internal"
        inferInternalFlagFromSubnet: false,
        disableManualOidcDialog: false,
      },
      useAmiBlockDeviceMappings: false,
    },
    cloudfoundry: {
      defaults: {
        account: 'my-cloudfoundry-account',
      },
    },
    ecs: {
      defaults: {
        account: 'test',
        region: 'us-east-1',
        iamRole: 'BaseIAMRole',
      },
      defaultSecurityGroups: [],
    },
    gce: {
      defaults: {
        account: 'my-google-account',
        instanceTypeStorage: {
          count: 1,
          defaultSettings: {
            disks: [
              {
                type: 'pd-ssd',
                sizeGb: 10,
              },
            ],
          },
          localSSDSupported: false,
          size: 10,
        },
        region: 'us-central1',
        zone: 'us-central1-f',
      },
      associatePublicIpAddress: true,
    },
    titus: {
      defaults: {
        account: 'titustestvpc',
        region: 'us-east-1',
        iamProfile: '{{application}}InstanceProfile',
      },
    },
    oracle: {
      defaults: {
        account: 'DEFAULT',
        region: 'us-phoenix-1',
        bakeryRegions: ['us-phoenix-1'],
      },
    },
    openstack: {
      defaults: {
        account: 'test',
        region: 'us-west-1',
      },
    },
    kubernetes: {
      defaults: {
        account: 'my-kubernetes-account',
        namespace: 'default',
        proxy: 'localhost:8001',
        internalDNSNameTemplate: '{{name}}.{{namespace}}.svc.cluster.local',
        instanceLinkTemplate: '{{host}}/api/v1/proxy/namespaces/{{namespace}}/pods/{{name}}',
        apiPrefix: 'api/v1/proxy/namespaces/kube-system/services/kubernetes-dashboard/#',
      },
    },
    dcos: {
      defaults: {
        account: 'my-dcos-account',
      },
    },
    appengine: {
      defaults: {
        account: 'my-appengine-account',
        containerImageUrlDeployments: false,
      },
    },
  },
  whatsNew: {
    gistId: '32526cd608db3d811b38',
    fileName: 'news.md',
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
    slack: {
      enabled: true,
      botName: 'spinnakerbot',
    },
  },
  pagerDuty: {
    required: false,
  },
  authEnabled: authEnabled,
  authTtl: 600000,
  gitSources: ['stash', 'github', 'bitbucket', 'gitlab'],
  pubsubProviders: ['google'], // TODO(joonlim): Add amazon once it is confirmed that amazon pub/sub works.
  triggerTypes: ['git', 'pipeline', 'docker', 'cron', 'jenkins', 'wercker', 'travis', 'pubsub'],
  searchVersion: 1,
  useClassicFirewallLabels: useClassicFirewallLabels,
  canary: {
    reduxLogger: reduxLoggerEnabled,
    metricsAccountName: canaryAccount,
    storageAccountName: canaryAccount,
    defaultJudge: 'NetflixACAJudge-v1.0',
    metricStore: defaultMetricStore,
    stagesEnabled: canaryStagesEnabled,
    atlasWebComponentsUrl: atlasWebComponentsUrl,
    templatesEnabled: templatesEnabled,
    showAllConfigs: true,
    featureDisabled: canaryFeatureDisabled,
  },
  feature: {
    artifacts: artifactsEnabled,
    canary: canaryEnabled,
    chaosMonkey: chaosEnabled,
    clusterDiff: false,
    dryRunEnabled: dryRunEnabled,
    entityTags: entityTagsEnabled,
    fastProperty: true,
    fiatEnabled: fiatEnabled,
    iapRefresherEnabled: iapRefresherEnabled,
    // whether stages affecting infrastructure (like "Create Load Balancer") should be enabled or not
    infrastructureStages: infrastructureEnabled,
    jobs: false,
    netflixMode: netflixMode,
    notifications: false,
    pagerDuty: false,
    pipelineTemplates: false,
    pipelines: true,
    roscoMode: false,
    snapshots: false,
    travis: false,
    versionedProviders: true,
    vpcMigrator: true,
    wercker: false,
    managedServiceAccounts: managedServiceAccountsEnabled,
  },
};
