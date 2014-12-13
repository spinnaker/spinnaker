'use strict';


angular.module('deckApp')
  .factory('awsImageService', function (settings, $q, Restangular, scheduledCache) {

    var gateEndpoint = Restangular.withConfig(function(RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.gateUrl);
    });

    function findImages(query, region, account) {
      var params = {q: query};
      if (region) {
        params.region = region;
      }
      if (account) {
        params.account = account;
      }
      params.provider = 'aws';
      if (query.length < 3) {
        return $q.when([{message: 'Please enter at least 3 characters...'}]);
      }
      return gateEndpoint.all('images/find').withHttpConfig({cache: scheduledCache}).getList(params, {}).then(function(results) {
          return results;
        },
        function() {
          return [];
        });
    }

    function getAmi(amiName, region, credentials) {
      return gateEndpoint.all('images').one(credentials).one(region).all(amiName).getList({provider: 'aws'}).then(function(results) {
          return results && results.length ? results[0] : null;
        },
        function() {
          return null;
        });
    }

    return {
      findImages: findImages,
      getAmi: getAmi,
    };
  });
