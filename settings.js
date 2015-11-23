'use strict';

let feedbackUrl = process.env.FEEDBACK_URL || 'http://hootch.test.netflix.net/submit';
let gateHost = process.env.API_HOST || 'https://spinnaker-api-prestaging.prod.netflix.net';
let bakeryDetailUrl = process.env.BAKERY_DETAIL_URL || 'http://bakery.test.netflix.net/#/?region={{context.region}}&package={{context.package}}&detail=bake:{{context.status.resourceId}}';
let authEndpoint = process.env.AUTH_ENDPOINT || 'https://spinnaker-api-prestaging.prod.netflix.net/auth/info';

window.spinnakerSettings = {
  defaultProviders: ['aws'],
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
        region: 'West US'
      },
      primaryAccounts: ['azure-test'],
      primaryRegions: ['West US', 'East US', 'Central US', 'North Central US', 'South Central US'],
      preferredZonesByAccount: {
	test: {
           'West US': ['West US'],
           'East US': ['East US'],
           'Central US': ['Central US'],
           'North Central US': ['North Central US'],
           'South Central US': ['South Central US']
        },
	default: {
           'West US': ['West US'],
           'East US': ['East US'],
           'Central US': ['Central US'],
           'North Central US': ['North Central US'],
           'South Central US': ['South Central US']
	}
     }
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
    }
  },
  whatsNew: {
    gistId: '32526cd608db3d811b38',
    fileName: 'news.md',
  },
  authEnabled: process.env.AUTH === 'enabled',
  feature: {
    pipelines: true,
    notifications: false,
    fastProperty: true,
    vpcMigrator: true,
    clusterDiff: true,
    rebakeControlEnabled: false,
    netflixMode: false,
  },
};
