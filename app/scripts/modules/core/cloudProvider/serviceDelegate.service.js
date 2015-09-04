'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.delegation', [
  require('./cloudProvider.registry.js'),
])
  .factory('serviceDelegate', function($injector, cloudProviderRegistry) {

    function getDelegate(provider, serviceKey) {
      let service = cloudProviderRegistry.getValue(provider, serviceKey);
      if ($injector.has(service)) {
        return $injector.get(service);
      } else {
        throw new Error('No "' + serviceKey + '" service found for provider "' + provider + '"');
      }
    }

    return {
      getDelegate: getDelegate,
    };
  })
  .name;
