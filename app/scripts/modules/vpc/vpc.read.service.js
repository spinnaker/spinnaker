'use strict';


angular
  .module('deckApp.vpc.read.service', ['restangular', 'deckApp.caches.infrastructure'])
  .factory('vpcReader', function ($q, Restangular, infrastructureCaches ) {

    var vpcsCache = [];

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

    return {
      listVpcs: listVpcs
    };

  });