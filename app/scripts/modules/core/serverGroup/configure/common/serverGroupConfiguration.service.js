'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.serverGroup.configure.common.configure.service', [
  require('../../../cloudProvider/serviceDelegate.service.js'),
])
.factory('serverGroupConfigurationService', function(serviceDelegate) {

  function getDelegate(provider) {
    return serviceDelegate.getDelegate(provider, 'serverGroup.configurationService');
  }

  function refreshInstanceTypes(provider, command) {
    return getDelegate(provider).refreshInstanceTypes(command);
  }

  return {
    refreshInstanceTypes: refreshInstanceTypes,
  };
});
