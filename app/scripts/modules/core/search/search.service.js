'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.search.service', [
  require('../cache/deckCacheFactory.js')
])
  .factory('searchService', function($q, $http, $log, settings) {

    var defaultPageSize = 500;

    function getFallbackResults() {
      return { results: [] };
    }

    function search(params) {
      var defaultParams = {
        pageSize: defaultPageSize
      };

      return $http({
        url: settings.gateUrl + '/search',
        params: angular.extend(defaultParams, params),
        timeout: settings.pollSchedule * 2 + 5000, // TODO: replace with apiHost call
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
