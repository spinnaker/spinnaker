'use strict';

let gateHost = '$GATE_HOST:$GATE_PORT';

window.spinnakerSettings = {
  gateUrl: `http://${gateHost}`,
  bakeryDetailUrl: 'http://$BAKERY_HOST:$BAKERY_PORT/api/v1/global/logs/{{context.status.id}}?html=true',
  pollSchedule: 30000,
  defaultTimeZone: 'America/New_York', // see http://momentjs.com/timezone/docs/#/data-utilities/
  providers: {
    gce: {
      defaults: {
        account: '$GOOGLE_PRIMARY_ACCOUNT_NAME',
        region: '$GOOGLE_DEFAULT_REGION',
        zone: '$GOOGLE_DEFAULT_ZONE',
      },
      primaryAccounts: ['$GOOGLE_PRIMARY_ACCOUNT_NAME'],
      challengeDestructiveActions: ['$GOOGLE_PRIMARY_ACCOUNT_NAME'],
    },
    aws: {
      defaults: {
        account: '$AWS_PRIMARY_ACCOUNT_NAME',
        region: '$AWS_DEFAULT_REGION'
      },
      primaryAccounts: ['$AWS_PRIMARY_ACCOUNT_NAME'],
      primaryRegions: ['eu-west-1', 'us-east-1', 'us-west-1', 'us-west-2'],
      challengeDestructiveActions: ['$AWS_PRIMARY_ACCOUNT_NAME'],
      preferredZonesByAccount: {
        $AWS_PRIMARY_ACCOUNT_NAME: {
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
  authEnabled: false,
  feature: {
    pipelines: true,
    notifications: false,
    canary: false,
    parallelPipelines: true,
    fastProperty: false,
    vpcMigrator: true,
  },
};
