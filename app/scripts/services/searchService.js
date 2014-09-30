'use strict';

var angular = require('angular');
angular.module('deckApp')
  .factory('searchService', function($q, $http, settings, $log) {

    var defaultParams = {
      pageSize: 100
    };

    function search(source, params) {
      return $http({
        url: settings[source + 'Url'] + '/search',
        params: angular.extend(defaultParams, params)
      })
        .then(
          function(response) {
            console.warn('data:', response.data[0]);
            return response.data[0];
          },
          function (response) {
            $log.error(response.data, response);
            return { results: [] };
          }
        );
    }

    return {
      search: search
    };
  });
