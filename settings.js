'use strict';

var feedbackUrl = process.env.FEEDBACK_URL || 'https://hootch.test.netflix.net/submit';
var gateHost = process.env.API_HOST || 'https://spinnaker-api-prestaging.mgmttest.netflix.net';
var bakeryDetailUrl = process.env.BAKERY_DETAIL_URL || 'http://bakery.test.netflix.net/#/?region={{context.region}}&package={{context.package}}&detail=bake:{{context.status.resourceId}}';
var authEndpoint = process.env.AUTH_ENDPOINT || 'https://spinnaker-api-prestaging.mgmttest.netflix.net/auth/user';
var authEnabled = process.env.AUTH_ENABLED === 'false' ? false : true;

window.spinnakerSettings = {
  checkForUpdates: true,
  defaultProviders: ['aws', 'gce', 'azure', 'cf', 'kubernetes', 'titus', 'openstack'],
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
        region: 'us-east-1'
      },
      defaultSecurityGroups: [],
      loadBalancers: {
        // if true, VPC load balancers will be created as internal load balancers if the selected subnet has a purpose
        // tag that starts with "internal"
        inferInternalFlagFromSubnet: false,
      },
      useAmiBlockDeviceMappings: false,
    },
    gce: {
      defaults: {
        account: 'my-google-account',
        region: 'us-central1',
        zone: 'us-central1-f',
      },
    },
    titus: {
      defaults: {
        account: 'titustestvpc',
        region: 'us-east-1'
      },
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
        namespace: 'default'
      },
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
  gitSources: ['stash', 'github'],
  triggerTypes: ['git', 'pipeline', 'docker', 'cron', 'jenkins'],
  feature: {
    pipelines: true,
    notifications: false,
    fastProperty: true,
    vpcMigrator: true,
    clusterDiff: true,
    roscoMode: false,
    netflixMode: false,
    // whether stages affecting infrastructure (like "Create Load Balancer") should be enabled or not
    infrastructureStages: process.env.INFRA_STAGES === 'enabled',
    jobs: false,
    serialization: false,
  },
};
