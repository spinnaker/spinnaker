'use strict';

var feedbackUrl = 'http://localhost';
var gateHost = process.env.API_HOST || 'http://localhost:8084';
var bakeryDetailUrl = gateHost + '/bakery/logs/global/{{context.status.id}}';
var authEndpoint = process.env.AUTH_ENDPOINT || (gateHost + '/auth/user');
var authEnabled = process.env.AUTH_ENABLED === 'true' ? true : false;
var fiatEnabled = process.env.FIAT_ENABLED === 'true' ? true : false;

window.spinnakerSettings = {
  defaultProviders: ['aws', 'gce', 'azure', 'cf', 'kubernetes', 'titan'],
  feedbackUrl: feedbackUrl,
  gateUrl: gateHost,
  authEndpoint: authEndpoint,
  bakeryDetailUrl: bakeryDetailUrl,
  pollSchedule: 30000,
  defaultTimeZone: 'America/New_York', // see http://momentjs.com/timezone/docs/#/data-utilities/
  providers: {
    azure: {
      defaults: {
        account: 'azure-test',
        region: 'West US'
      },
    },
    aws: {
      defaults: {
        account: 'test',
        region: 'us-east-1'
      },
      defaultSecurityGroups: ['nf-datacenter-vpc', 'nf-infrastructure-vpc', 'nf-datacenter', 'nf-infrastructure'],
      loadBalancers: {
        // if true, VPC load balancers will be created as internal load balancers if the selected subnet has a purpose
        // tag that starts with "internal"
        inferInternalFlagFromSubnet: false,
      },
    },
    gce: {
      defaults: {
        account: 'my-google-account',
        region: 'us-central1',
        zone: 'us-central1-f',
      },
    },
    titan: {
      defaults: {
        account: 'titustest',
        region: 'us-east-1'
      },
    },
    kubernetes: {
      defaults: {
        account: 'my-kubernetes-account',
        namespace: 'default'
      },
    }
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
  whatsNew: {
    gistId: '32526cd608db3d811b38',
    fileName: 'news.md',
  },
  authEnabled: authEnabled,
  feature: {
    fiatEnabled: fiatEnabled,
    pipelines: true,
    jobs: true,
    notifications: false,
    fastProperty: false,
    vpcMigrator: false,
    clusterDiff: false,
    roscoMode: false,
    netflixMode: false,
    infrastructureStages: false, // Should 'createLoadBalancer' be a pipeline stage? (no).
  },
};
