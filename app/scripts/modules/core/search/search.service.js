'use strict';

let angular = require('angular');

import {SETTINGS} from 'core/config/settings';

module.exports = angular.module('spinnaker.core.search.service', [])
  .factory('searchService', function($q, $http, $log) {

    var defaultPageSize = 500;

    function getFallbackResults() {
      return { results: [] };
    }

    function search(params) {
      var defaultParams = {
        pageSize: defaultPageSize
      };

      return $http({
        url: SETTINGS.gateUrl + '/search',
        params: angular.extend(defaultParams, params),
        timeout: SETTINGS.pollSchedule * 2 + 5000, // TODO: replace with apiHost call
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
