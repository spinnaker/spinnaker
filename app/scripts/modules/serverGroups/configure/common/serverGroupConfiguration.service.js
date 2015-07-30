'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.common.configure.service', [
  require('../aws/serverGroupConfiguration.service.js'),
  require('../gce/serverGroupConfiguration.service.js')
])
.factory('serverGroupConfigurationService', function(awsServerGroupConfigurationService, gceServerGroupConfigurationService) {

  function getDelegate(provider) {
    return (!provider || provider === 'aws') ? awsServerGroupConfigurationService : gceServerGroupConfigurationService;
  }

  function refreshInstanceTypes(provider, command) {
    return getDelegate(provider).refreshInstanceTypes(command);
  }

  return {
    refreshInstanceTypes: refreshInstanceTypes,
  };
})
.name;
