'use strict';

angular
  .module('spinnaker.keyPairs.read.service', ['restangular', 'spinnaker.caches.infrastructure'])
  .factory('keyPairsReader', function ($q, Restangular, infrastructureCaches) {

    function listKeyPairs() {
      return Restangular.all('keyPairs')
        .withHttpConfig({cache: infrastructureCaches.keyPairs})
        .getList();
    }

    return {
      listKeyPairs: listKeyPairs
    };

  });
