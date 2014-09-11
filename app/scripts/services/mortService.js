'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('mortService', function (searchService, settings, $q, Restangular) {

    var subnetsCache = [];

    var endpoint = Restangular.withConfig(function(RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.mortUrl);
      RestangularConfigurer.setDefaultHeaders( {'Content-Type':'application/context+json'} );
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

    return {
      listSubnets: listSubnets
    };
  });
