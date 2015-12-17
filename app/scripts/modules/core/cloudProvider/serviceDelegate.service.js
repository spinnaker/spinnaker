'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.delegation', [
  require('./cloudProvider.registry.js'),
])
  .factory('serviceDelegate', function($injector, cloudProviderRegistry, _) {

    let hasDelegate = _.memoize((provider, serviceKey) => {
      let service = cloudProviderRegistry.getValue(provider, serviceKey);
      return $injector.has(service);
    }, (provider, serviceKey) => { return provider + '#' + serviceKey; });

    let getDelegate = _.memoize((provider, serviceKey) => {
      let service = cloudProviderRegistry.getValue(provider, serviceKey);
      if ($injector.has(service)) {
        return $injector.get(service);
      } else {
        throw new Error('No "' + serviceKey + '" service found for provider "' + provider + '"');
      }
    }, (provider, serviceKey) => { return provider + '#' + serviceKey; });

    return {
      hasDelegate: hasDelegate,
      getDelegate: getDelegate,
    };
  });
