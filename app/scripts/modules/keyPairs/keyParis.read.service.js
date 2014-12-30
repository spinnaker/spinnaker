'use strict';

angular
  .module('deckApp.keyPairs.read.service', ['restangular', 'deckApp.caches.infrastructure'])
  .factory('keyPairsReader', function ($q, Restangular, infrastructureCaches) {

    var  keyPairsCache = [];

    function listKeyPairs() {
      if (keyPairsCache.length) {
        return $q.when(keyPairsCache);
      } else {
        var deferred = $q.defer();
        Restangular.all('keyPairs')
          .withHttpConfig({cache: infrastructureCaches.keyPairs})
          .getList().then(function(list) {
            keyPairsCache = list;
            deferred.resolve(list);
          });
        return deferred.promise;
      }
    }

    return {
      listKeyPairs: listKeyPairs
    };

  });