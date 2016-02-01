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
// var authEnabled = ${services.deck.auth.enabled};
// var defaultTimeZone = ${services.deck.timezone};
// var awsDefaultRegion = ${providers.aws.defaultRegion};
// var awsPrimaryAccount = ${providers.aws.primaryCredentials.name};
// var googleDefaultRegion = ${providers.google.defaultRegion};
// var googleDefaultZone = ${providers.google.defaultZone};
// var googlePrimaryAccount = ${providers.google.primaryCredentials.name};
// var azureDefaultRegion = ${providers.azure.defaultRegion};
// var azurePrimaryAccount = ${providers.azure.primaryCredentials.name};
// var cfDefaultRegion = ${providers.cf.defaultOrg};
// var cfDefaultZone = ${providers.cf.defaultSpace};
// var cfPrimaryAccount = ${providers.cf.primaryCredentials.name};
// var titanDefaultRegion = ${providers.titan.defaultRegion};
// var titanPrimaryAccount = ${providers.titan.primaryCredentials.name};
// var kubernetesDefaultNamespace = ${providers.kubernetes.primaryCredentials.namespace};
// var kubernetesPrimaryAccount = ${providers.kubernetes.primaryCredentials.name};

// END reconfigure_spinnaker
/**
 * Any additional custom var statements can go below without
 * being affected by scripts/reconfigure_spinnaker.sh
 */

window.spinnakerSettings = {
  gateUrl: gateUrl,
  bakeryDetailUrl: bakeryBaseUrl + '/api/v1/global/logs/{{context.status.id}}?html=true',
  authEndpoint: gateUrl + '/auth/info',
  pollSchedule: 30000,
  defaultTimeZone: defaultTimeZone, // see http://momentjs.com/timezone/docs/#/data-utilities/
  providers: {
    azure: {
      defaults: {
        account: azurePrimaryAccount,
        region: azureDefaultRegion
      },
    },
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
    },
    titan: {
      defaults: {
        account: titanPrimaryAccount,
        region: titanDefaultRegion
      },
    },
    kubernetes: {
      defaults: {
        account: kubernetesPrimaryAccount,
        namespace: kubernetesDefaultNamespace
      },
    }
  },
  authEnabled: authEnabled,
  feature: {
    pipelines: true,
    notifications: false,
    fastProperty: false,
    vpcMigrator: false,
    clusterDiff: false,
    roscoMode: true,
    netflixMode: false,
  },
};
