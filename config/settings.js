'use strict';


/**
 * This section is managed by scripts/reconfigure_spinnaker.sh
 * If hand-editing, only add comment lines that look like
 * '// let VARIABLE = VALUE'
 * and let scripts/reconfigure manage the actual values.
 */
// BEGIN reconfigure_spinnaker

// let gateUrl = ${services.gate.baseUrl};
// let bakeryBaseUrl = ${services.bakery.baseUrl};
// let awsDefaultRegion = ${providers.aws.defaultRegion};
// let awsPrimaryAccount = ${providers.aws.primaryCredentials.name};
// let googleDefaultRegion = ${providers.google.defaultRegion};
// let googleDefaultZone = ${providers.google.defaultZone};
// let googlePrimaryAccount = ${providers.google.primaryCredentials.name};
// let cfDefaultRegion = ${providers.cf.defaultOrg};
// let cfDefaultZone = ${providers.cf.defaultSpace};
// let cfPrimaryAccount = ${providers.cf.primaryCredentials.name};

// END reconfigure_spinnaker
/**
 * Any additional custom let statements can go below without
 * being affected by scripts/reconfigure_spinnaker.sh
 */

window.spinnakerSettings = {
  gateUrl: `${gateUrl}`,
  bakeryDetailUrl: `${bakeryBaseUrl}/api/v1/global/logs/{{context.status.id}}?html=true`,
  pollSchedule: 30000,
  defaultTimeZone: 'America/New_York', // see http://momentjs.com/timezone/docs/#/data-utilities/
  providers: {
    gce: {
      defaults: {
        account: `${googlePrimaryAccount}`,
        region: `${googleDefaultRegion}`,
        zone: `${googleDefaultZone}`,
      },
      primaryAccounts: [`${googlePrimaryAccount}`],
      challengeDestructiveActions: [`${googlePrimaryAccount}`],
    },
    aws: {
      defaults: {
        account: `${awsPrimaryAccount}`,
        region: `${awsDefaultRegion}`
      },
      primaryAccounts: [`${awsPrimaryAccount}`],
      primaryRegions: ['eu-west-1', 'us-east-1', 'us-west-1', 'us-west-2'],
      challengeDestructiveActions: [`${awsPrimaryAccount}`],
      preferredZonesByAccount: {}
    },
    cf: {
      defaults: {
        account: `${cfPrimaryAccount}`,
        region: `${cfDefaultRegion}`
      },
      primaryAccounts: [`${cfPrimaryAccount}`],
      primaryRegions: ['production'],
      challengeDestructiveActions: [`${cfPrimaryAccount}`],
      preferredZonesByAccount: {}
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
    vpcMigrator: false,
  },
};

window.spinnakerSettings.providers.aws.preferredZonesByAccount[`${awsPrimaryAccount}`] = {
  'us-east-1': ['us-east-1a', 'us-east-1b', 'us-east-1d', 'us-east-1e'],
  'us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c'],
  'us-west-2': ['us-west-2a', 'us-west-2b', 'us-west-2c'],
  'eu-west-1': ['eu-west-1a', 'eu-west-1b', 'eu-west-1c'],
  'ap-northeast-1': ['ap-northeast-1a', 'ap-northeast-1b', 'ap-northeast-1c'],
  'ap-southeast-1': ['ap-southeast-1a', 'ap-southeast-1b'],
  'ap-southeast-2': ['ap-southeast-2a', 'ap-southeast-2b'],
  'sa-east-1': ['sa-east-1a', 'sa-east-1b']
};
