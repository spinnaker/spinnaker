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
  defaultTimeZone: 'America/Los_Angeles', // see http://momentjs.com/timezone/docs/#/data-utilities/
  providers: {
    aws: {
      defaults: {
        account: 'test',
        region: 'us-east-1'
      },
      primaryAccounts: ['prod', 'test'],
      primaryRegions: ['eu-west-1', 'us-east-1', 'us-west-1', 'us-west-2'],
      challengeDestructiveActions: ['mgmt', 'prod', 'mceprod', 'cpl'],
      defaultSecurityGroups: ['nf-datacenter-vpc', 'nf-infrastructure-vpc', 'nf-datacenter', 'nf-infrastructure'],
      accountBastions : {
        'prod': 'aws.prod.netflix.net',
        'test': 'aws.test.netflix.net',
        'mgmt': 'aws.mgmt.netflix.net',
        'mcetest': 'awsmce.test.netflix.net',
        'mceprod': 'awsmce.prod.netflix.net',
        'cpl': 'awscpl.prod.netflix.net',
      },
      preferredZonesByAccount: {
        prod: {
          'us-east-1': ['us-east-1c', 'us-east-1d', 'us-east-1e'],
          'us-west-1': ['us-west-1a', 'us-west-1c'],
          'us-west-2': ['us-west-2a', 'us-west-2b', 'us-west-2c'],
          'eu-west-1': ['eu-west-1a', 'eu-west-1b', 'eu-west-1c'],
          'ap-northeast-1': ['ap-northeast-1a', 'ap-northeast-1b', 'ap-northeast-1c'],
          'ap-southeast-1': ['ap-southeast-1a', 'ap-southeast-1b'],
          'ap-southeast-2': ['ap-southeast-2a', 'ap-southeast-2b'],
          'sa-east-1': ['sa-east-1a', 'sa-east-1b']
        },
        test: {
          'us-east-1': ['us-east-1c', 'us-east-1d', 'us-east-1e'],
          'us-west-1': ['us-west-1a', 'us-west-1c'],
          'us-west-2': ['us-west-2a', 'us-west-2b', 'us-west-2c'],
          'eu-west-1': ['eu-west-1a', 'eu-west-1b', 'eu-west-1c'],
          'ap-northeast-1': ['ap-northeast-1a', 'ap-northeast-1b', 'ap-northeast-1c'],
          'ap-southeast-1': ['ap-southeast-1a', 'ap-southeast-1b'],
          'ap-southeast-2': ['ap-southeast-2a', 'ap-southeast-2b'],
          'sa-east-1': ['sa-east-1a', 'sa-east-1b']
        },
        mgmt: {
          'us-east-1': ['us-east-1c', 'us-east-1d', 'us-east-1e'],
          'us-west-1': ['us-west-1a', 'us-west-1c'],
          'us-west-2': ['us-west-2a', 'us-west-2b', 'us-west-2c'],
          'eu-west-1': ['eu-west-1a', 'eu-west-1b', 'eu-west-1c'],
          'ap-northeast-1': ['ap-northeast-1a', 'ap-northeast-1b', 'ap-northeast-1c'],
          'ap-southeast-1': ['ap-southeast-1a', 'ap-southeast-1b'],
          'ap-southeast-2': ['ap-southeast-2a', 'ap-southeast-2b'],
          'sa-east-1': ['sa-east-1a', 'sa-east-1b']
        },
        mgmttest: {
          'us-east-1': ['us-east-1c', 'us-east-1d', 'us-east-1e'],
          'us-west-1': ['us-west-1a', 'us-west-1c'],
          'us-west-2': ['us-west-2a', 'us-west-2b', 'us-west-2c'],
          'eu-west-1': ['eu-west-1a', 'eu-west-1b', 'eu-west-1c'],
          'ap-northeast-1': ['ap-northeast-1a', 'ap-northeast-1b', 'ap-northeast-1c'],
          'ap-southeast-1': ['ap-southeast-1a', 'ap-southeast-1b'],
          'ap-southeast-2': ['ap-southeast-2a', 'ap-southeast-2b'],
          'sa-east-1': ['sa-east-1a', 'sa-east-1b']
        },
        mceprod: {
          'us-east-1': ['us-east-1a', 'us-east-1b', 'us-east-1c', 'us-east-1d', 'us-east-1e'],
          'us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c'],
          'us-west-2': ['us-west-2a', 'us-west-2b', 'us-west-2c'],
          'eu-west-1': ['eu-west-1a', 'eu-west-1b', 'eu-west-1c'],
          'ap-northeast-1': ['ap-northeast-1a', 'ap-northeast-1b', 'ap-northeast-1c'],
          'ap-southeast-1': ['ap-southeast-1a', 'ap-southeast-1b'],
          'ap-southeast-2': ['ap-southeast-2a', 'ap-southeast-2b'],
          'sa-east-1': ['sa-east-1a', 'sa-east-1b']
        },
        mcetest: {
          'us-east-1': ['us-east-1a', 'us-east-1b', 'us-east-1c', 'us-east-1d'],
          'us-west-1': ['us-west-1b', 'us-west-1c'],
          'us-west-2': ['us-west-2a', 'us-west-2b', 'us-west-2c'],
          'eu-west-1': ['eu-west-1a', 'eu-west-1b', 'eu-west-1c'],
          'ap-northeast-1': ['ap-northeast-1a', 'ap-northeast-1b', 'ap-northeast-1c'],
          'ap-southeast-1': ['ap-southeast-1a', 'ap-southeast-1b'],
          'ap-southeast-2': ['ap-southeast-2a', 'ap-southeast-2b'],
          'sa-east-1': ['sa-east-1a', 'sa-east-1b']
        },
        default: {
          'us-east-1': ['us-east-1a', 'us-east-1b', 'us-east-1c', 'us-east-1d', 'us-east-1e'],
          'us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c'],
          'us-west-2': ['us-west-2a', 'us-west-2b', 'us-west-2c'],
          'eu-west-1': ['eu-west-1a', 'eu-west-1b', 'eu-west-1c'],
          'ap-northeast-1': ['ap-northeast-1a', 'ap-northeast-1b', 'ap-northeast-1c'],
          'ap-southeast-1': ['ap-southeast-1a', 'ap-southeast-1b'],
          'ap-southeast-2': ['ap-southeast-2a', 'ap-southeast-2b'],
          'sa-east-1': ['sa-east-1a', 'sa-east-1b']
        }
      }
    },
    titan: {
      defaults: {
        account: 'titantest',
        region: 'us-east-1'
      },
      primaryAccounts: ['test'],
      primaryRegions: ['us-east-1'],
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
  },
};
