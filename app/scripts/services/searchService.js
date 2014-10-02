'use strict';

var angular = require('angular');
angular.module('deckApp')
  .factory('searchService', function($q, $http, settings, $log) {

    function getFallbackResults() {
      return { results: [] };
    }

    function search(source, params) {
      var defaultParams = {
        pageSize: 100
      };

      return $http({
        url: settings[source + 'Url'] + '/search',
        params: angular.extend(defaultParams, params)
      })
        .then(
          function(response) {
            return response.data[0];
          },
          function (response) {
            $log.error(response.data, response);
            return getFallbackResults();
          }
        );
    }

    return {
      search: search,
      getFallbackResults: getFallbackResults
    };
  });
