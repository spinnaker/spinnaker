'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.delegation', [])
  .factory('serviceDelegate', function($injector) {

    function getDelegate(provider, serviceBaseName) {
      provider = provider || 'aws';
      var delegate = provider + serviceBaseName;
      if ($injector.has(delegate)) {
        return $injector.get(delegate);
      } else {
        throw new Error('No "' + serviceBaseName + '" service found for provider "' + provider + '"');
      }
    }

    return {
      getDelegate: getDelegate,
    };
  });
