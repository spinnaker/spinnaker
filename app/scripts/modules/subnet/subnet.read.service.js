'use strict';

angular
  .module('deckApp.subnet.read.service', ['restangular', 'deckApp.caches.infrastructure'])
  .factory('subnetReader', function ($q, Restangular, infrastructureCaches) {

    var subnetsCache = [];

    function listSubnets() {
      if (subnetsCache.length) {
        return $q.when(subnetsCache);
      } else {
        var deferred = $q.defer();
        Restangular.all('subnets')
          .withHttpConfig({cache: infrastructureCaches.subnets})
          .getList().then(function(list) {
            subnetsCache = list;
            deferred.resolve(list);
          });
        return deferred.promise;
      }
    }

    return {
      listSubnets: listSubnets
    };

  });
