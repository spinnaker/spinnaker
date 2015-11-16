'use strict';


/**
 * This section is managed by scripts/reconfigure_spinnaker.sh
 * If hand-editing, only add comment lines that look like
 * '// var VARIABLE = VALUE'
 * and let scripts/reconfigure manage the actual values.
 */
// BEGIN reconfigure_spinnaker

// var gateUrl = ${services.gate.baseUrl};
// var bakeryBaseUrl = ${services.bakery.baseUrl};
// var awsDefaultRegion = ${providers.aws.defaultRegion};
// var awsPrimaryAccount = ${providers.aws.primaryCredentials.name};
// var googleDefaultRegion = ${providers.google.defaultRegion};
// var googleDefaultZone = ${providers.google.defaultZone};
// var googlePrimaryAccount = ${providers.google.primaryCredentials.name};
// let cfDefaultRegion = ${providers.cf.defaultOrg};
// let cfDefaultZone = ${providers.cf.defaultSpace};
// let cfPrimaryAccount = ${providers.cf.primaryCredentials.name};

// END reconfigure_spinnaker
/**
 * Any additional custom var statements can go below without
 * being affected by scripts/reconfigure_spinnaker.sh
 */

window.spinnakerSettings = {
  gateUrl: gateUrl,
  bakeryDetailUrl: bakeryBaseUrl + '/api/v1/global/logs/{{context.status.id}}?html=true',
  pollSchedule: 30000,
  defaultTimeZone: 'America/New_York', // see http://momentjs.com/timezone/docs/#/data-utilities/
  providers: {
    gce: {
      defaults: {
        account: googlePrimaryAccount,
        region: googleDefaultRegion,
        zone: googleDefaultZone,
      }
    },
    aws: {
      defaults: {
        account: awsPrimaryAccount,
        region: awsDefaultRegion
      }
    },
    cf: {
      defaults: {
        account: cfPrimaryAccount,
        region: cfDefaultRegion
      },
    }
  },
  authEnabled: false,
  feature: {
    pipelines: true,
    notifications: false,
    rebakeControlEnabled: true,
    netflixMode: false,
  },
};
