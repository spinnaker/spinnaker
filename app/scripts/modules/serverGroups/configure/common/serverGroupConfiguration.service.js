'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.common.configure.service', [
  require('../../../delegation/serviceDelegate.service.js'),
])
.factory('serverGroupConfigurationService', function(serviceDelegate) {

  function getDelegate(provider) {
    return serviceDelegate.getDelegate(provider, 'ServerGroupConfigurationService');
  }

  function refreshInstanceTypes(provider, command) {
    return getDelegate(provider).refreshInstanceTypes(command);
  }

  return {
    refreshInstanceTypes: refreshInstanceTypes,
  };
})
.name;
