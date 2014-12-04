'use strict';


angular.module('deckApp')
  .factory('awsImageService', function (settings, $q, Restangular) {

    var oortEndpoint = Restangular.withConfig(function(RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.oortUrl);
    });

    function findImages(query, region, account) {
      if (query.length < 3) {
        return $q.when([{message: 'Please enter at least 3 characters...'}]);
      }
      return oortEndpoint.all('aws/images/find').getList({q: query, region: region}, {}).then(function(results) {
          return results;
        },
        function() {
          return [];
        });
    }

    function getAmi(amiName, region, credentials) {
      return oortEndpoint.all('aws/images').one(credentials).one(region).all(amiName).getList().then(function(results) {
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
