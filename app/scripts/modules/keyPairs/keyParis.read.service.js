'use strict';

angular
  .module('deckApp.keyPairs.read.service', ['restangular', 'deckApp.caches.infrastructure'])
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
