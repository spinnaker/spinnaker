'use strict';

let feedbackUrl = process.env.FEEDBACK_URL || 'http://hootch.test.netflix.net/submit';
let gateHost = process.env.API_HOST || 'localhost:8084';
let bakeryDetailUrl = process.env.BAKERY_DETAIL_URL || 'http://localhost:8087/api/v1/global/logs/{{context.status.id}}?html=true';
let authEndpoint = process.env.AUTH_ENDPOINT || 'spinnaker-api-prestaging.prod.netflix.net/auth/info';

let protocol = process.env.PROTOCOL || 'http';

window.spinnakerSettings = {
  feedbackUrl: feedbackUrl,
  gateUrl: `http://${gateHost}`,
  bakeryDetailUrl: bakeryDetailUrl,
  pollSchedule: 30000,
  defaultTimeZone: 'America/New_York', // see http://momentjs.com/timezone/docs/#/data-utilities/
  providers: {
    gce: {
      defaults: {
        account: '$GOOGLE_ACCOUNT_NAME',
        region: 'us-central1',
        zone: 'us-central1-f',
      },
      primaryAccounts: ['$GOOGLE_ACCOUNT_NAME'],
      challengeDestructiveActions: ['$GOOGLE_ACCOUNT_NAME'],
    },
    aws: {
      defaults: {
        account: 'default',
        region: 'us-east-1'
      },
      primaryAccounts: ['default'],
      primaryRegions: ['eu-west-1', 'us-east-1', 'us-west-1', 'us-west-2'],
      challengeDestructiveActions: ['default'],
      preferredZonesByAccount: {
        default: {
          'us-east-1': ['us-east-1a', 'us-east-1b', 'us-east-1d', 'us-east-1e'],
          'us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c'],
          'us-west-2': ['us-west-2a', 'us-west-2b', 'us-west-2c'],
          'eu-west-1': ['eu-west-1a', 'eu-west-1b', 'eu-west-1c'],
          'ap-northeast-1': ['ap-northeast-1a', 'ap-northeast-1b', 'ap-northeast-1c'],
          'ap-southeast-1': ['ap-southeast-1a', 'ap-southeast-1b'],
          'ap-southeast-2': ['ap-southeast-2a', 'ap-southeast-2b'],
          'sa-east-1': ['sa-east-1a', 'sa-east-1b']
        }
      }
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
    canary: process.env.CANARY !== 'disabled',
    parallelPipelines: true,
    fastProperty: false,
    vpcMigrator: true,
  },
};
