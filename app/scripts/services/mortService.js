'use strict';


angular.module('deckApp')
  .factory('mortService', function (settings, $q, Restangular) {

    var subnetsCache = [],
        vpcsCache = [],
        keyPairsCache = [];

    var endpoint = Restangular.withConfig(function(RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.mortUrl);
    });

    function listSubnets() {
      if (subnetsCache.length) {
        return $q.when(subnetsCache);
      } else {
        var deferred = $q.defer();
        endpoint.all('subnets').getList().then(function(list) {
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
        endpoint.all('vpcs').getList().then(function(list) {
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
        endpoint.all('keyPairs').getList().then(function(list) {
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
