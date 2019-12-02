import { AZURE_SERVERGROUP_DETAILS_SERVERGROUPDETAILS_AZURE_CONTROLLER } from './serverGroupDetails.azure.controller';
('use strict');

const angular = require('angular');

export const AZURE_SERVERGROUP_DETAILS_SERVERGROUP_DETAILS_MODULE = 'spinnaker.azure.serverGroup.details.azure';
export const name = AZURE_SERVERGROUP_DETAILS_SERVERGROUP_DETAILS_MODULE; // for backwards compatibility
angular.module(AZURE_SERVERGROUP_DETAILS_SERVERGROUP_DETAILS_MODULE, [
  AZURE_SERVERGROUP_DETAILS_SERVERGROUPDETAILS_AZURE_CONTROLLER,
]);
