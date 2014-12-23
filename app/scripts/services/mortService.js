'use strict';


angular.module('deckApp')
  .factory('mortService', function (settings, $q, Restangular, infrastructureCaches) {

    var subnetsCache = [],
        vpcsCache = [],
        keyPairsCache = [];

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

    function listVpcs() {
      if (vpcsCache.length) {
        return $q.when(vpcsCache);
      } else {
        var deferred = $q.defer();
        Restangular.all('vpcs')
          .withHttpConfig({cache: infrastructureCaches.vpcs})
          .getList().then(function(list) {
          vpcsCache = list;
          deferred.resolve(list);
        });
        return deferred.promise;
      }
    }

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
      listSubnets: listSubnets,
      listVpcs: listVpcs,
      listKeyPairs: listKeyPairs
    };
  });
