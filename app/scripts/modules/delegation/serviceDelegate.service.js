'use strict';

angular.module('deckApp.delegation', [])
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
