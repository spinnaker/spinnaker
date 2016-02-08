'use strict';

var feedbackUrl = process.env.FEEDBACK_URL || 'http://hootch.test.netflix.net/submit';
var gateHost = process.env.API_HOST || 'https://spinnaker-api-prestaging.prod.netflix.net';
var bakeryDetailUrl = process.env.BAKERY_DETAIL_URL || 'http://bakery.test.netflix.net/#/?region={{context.region}}&package={{context.package}}&detail=bake:{{context.status.resourceId}}';
var authEndpoint = process.env.AUTH_ENDPOINT || 'https://spinnaker-api-prestaging.prod.netflix.net/auth/info';

window.spinnakerSettings = {
  defaultProviders: ['aws', 'gce', 'azure', 'cf', 'kubernetes'],
  feedbackUrl: feedbackUrl,
  gateUrl: gateHost,
  bakeryDetailUrl: bakeryDetailUrl,
  authEndpoint: authEndpoint,
  pollSchedule: 30000,
  defaultTimeZone: process.env.TIMEZONE || 'America/Los_Angeles', // see http://momentjs.com/timezone/docs/#/data-utilities/
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
      defaultSecurityGroups: ['nf-datacenter-vpc', 'nf-infrastructure-vpc', 'nf-datacenter', 'nf-infrastructure'],
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
        account: 'titantest',
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
  whatsNew: {
    gistId: '32526cd608db3d811b38',
    fileName: 'news.md',
  },
  authEnabled: process.env.AUTH === 'enabled',
  gitSources: ['stash', 'github'],
  feature: {
    pipelines: true,
    notifications: false,
    fastProperty: true,
    vpcMigrator: true,
    clusterDiff: true,
    roscoMode: false,
    netflixMode: false,
  },
};
