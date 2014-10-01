'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('mortService', function (settings, $q, Restangular) {

    var subnetsCache = [],
        vpcsCache = [];

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

    return {
      listSubnets: listSubnets,
      listVpcs: listVpcs
    };
  });
