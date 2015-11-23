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
      }
    },
    aws: {
      defaults: {
        account: `${awsPrimaryAccount}`,
        region: `${awsDefaultRegion}`
      }
    }
  },
  authEnabled: false,
  feature: {
    pipelines: true,
    notifications: false,
    fastProperty: false,
    vpcMigrator: false,
    rebakeControlEnabled: true,
    netflixMode: false,
  },
};
