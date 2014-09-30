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
      var deferred = $q.defer();
      if (subnetsCache.length) {
        deferred.resolve(subnetsCache);
      } else {
        endpoint.all('subnets').getList().then(function(list) {
          subnetsCache = list;
          deferred.resolve(list);
        });
      }
      return deferred.promise;
    }

    function listVpcs() {
      var deferred = $q.defer();
      if (vpcsCache.length) {
        deferred.resolve(vpcsCache);
      } else {
        endpoint.all('vpcs').getList().then(function(list) {
          vpcsCache = list;
          deferred.resolve(list);
        });
      }
      return deferred.promise;
    }

    return {
      listSubnets: listSubnets,
      listVpcs: listVpcs
    };
  });
