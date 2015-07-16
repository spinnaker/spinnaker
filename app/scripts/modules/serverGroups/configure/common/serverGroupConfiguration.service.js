'use strict';

angular.module('spinnaker.serverGroup.configure.common.configure.service', [
  'spinnaker.aws.serverGroup.configure.service',
  'spinnaker.gce.serverGroup.configure.service',
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
});