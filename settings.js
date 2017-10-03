'use strict';

// Add any env variables used here to the webpack config variable: HAPPY_PACK_ENV_INVALIDATE
var feedbackUrl = process.env.FEEDBACK_URL;
var gateHost = process.env.API_HOST || 'http://localhost:8084';
var bakeryDetailUrl = process.env.BAKERY_DETAIL_URL || (gateHost + '/bakery/logs/{{context.region}}/{{context.status.resourceId}}');
var authEndpoint = process.env.AUTH_ENDPOINT || (gateHost + '/auth/user');
var authEnabled = process.env.AUTH_ENABLED === 'false' ? false : true;
var netflixMode = process.env.NETFLIX_MODE === 'true' ? true : false;
var chaosEnabled = netflixMode || process.env.CHAOS_ENABLED === 'true' ? true : false;
var fiatEnabled = process.env.FIAT_ENABLED === 'true' ? true : false;
var entityTagsEnabled = process.env.ENTITY_TAGS_ENABLED === 'true' ? true : false;
var debugEnabled = process.env.DEBUG_ENABLED === 'false' ? false : true;
var canaryEnabled = process.env.CANARY_ENABLED === 'true';
var infrastructureEnabled = process.env.INFRA_ENABLED === 'true' ? true : false;

window.spinnakerSettings = {
  checkForUpdates: true,
  debugEnabled: debugEnabled,
  defaultProviders: ['aws', 'gce', 'azure', 'cf', 'kubernetes', 'dcos', 'openstack', 'oraclebmcs', 'ecs'],
  feedbackUrl: feedbackUrl,
  gateUrl: gateHost,
  bakeryDetailUrl: bakeryDetailUrl,
  authEndpoint: authEndpoint,
  pollSchedule: 30000,
  defaultTimeZone: process.env.TIMEZONE || 'America/Los_Angeles', // see http://momentjs.com/timezone/docs/#/data-utilities/
  defaultCategory: 'serverGroup',
  defaultInstancePort: 80,
  providers: {
    azure: {
      defaults: {
        account: 'azure-test',
        region: 'westus'
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
      },
      useAmiBlockDeviceMappings: false,
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
    oraclebmcs: {
      defaults: {
        account: 'DEFAULT',
        region: 'us-phoenix-1',
        bakeryRegions: ['us-phoenix-1']
      }
    },
    openstack: {
      defaults: {
        account: 'test',
        region: 'us-west-1'
      },
    },
    kubernetes: {
      defaults: {
        account: 'my-kubernetes-account',
        namespace: 'default',
        proxy: 'localhost:8001',
        internalDNSNameTemplate: '{{name}}.{{namespace}}.svc.cluster.local',
        instanceLinkTemplate: '{{host}}/api/v1/proxy/namespaces/{{namespace}}/pods/{{name}}',
      },
    },
    dcos: {
      defaults: {
        account: 'my-dcos-account'
      },
    },
    appengine: {
      defaults: {
        account: 'my-appengine-account',
        editLoadBalancerStageEnabled: false,
      }
    }
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
      botName: 'Skynet T-800'
    },
    sms: {
      enabled: true,
    },
    slack: {
      enabled: true,
      botName: 'spinnakerbot'
    }
  },
  authEnabled: authEnabled,
  authTtl: 600000,
  gitSources: ['stash', 'github', 'bitbucket'],
  triggerTypes: ['git', 'pipeline', 'docker', 'cron', 'jenkins', 'travis', 'pubsub'],
  feature: {
    canary: canaryEnabled,
    entityTags: entityTagsEnabled,
    fiatEnabled: fiatEnabled,
    pipelines: true,
    notifications: false,
    fastProperty: true,
    vpcMigrator: true,
    clusterDiff: false,
    roscoMode: false,
    netflixMode: netflixMode,
    chaosMonkey: chaosEnabled,
    // whether stages affecting infrastructure (like "Create Load Balancer") should be enabled or not
    infrastructureStages: infrastructureEnabled,
    jobs: false,
    snapshots: false,
    travis: false,
    pipelineTemplates: false,
    artifacts: false,
    versionedProviders: false,
  },
};
