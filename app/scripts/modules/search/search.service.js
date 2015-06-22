'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.search.service', [
  require('../caches/deckCacheFactory.js')
])
  .factory('searchService', function($q, $http, $log, settings) {

    var defaultPageSize = 200;

    function getFallbackResults() {
      return { results: [] };
    }

    function search(params) {
      var defaultParams = {
        pageSize: defaultPageSize
      };

      return $http({
        url: settings.gateUrl + '/search',
        params: angular.extend(defaultParams, params)
      })
        .then(
          function(response) {
            return response.data[0] || getFallbackResults();
          },
          function (response) {
            $log.error(response.data, response);
            return getFallbackResults();
          }
        );
    }

    return {
      search: search,
      getFallbackResults: getFallbackResults,
      defaultPageSize: defaultPageSize,
    };
  });
