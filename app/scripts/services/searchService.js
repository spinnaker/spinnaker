'use strict';

var angular = require('angular');
angular.module('deckApp')
  .factory('searchService', function($q, $http, settings) {

    function search(params) {
      var defaultParams = {
        pageSize: 100
      };

      return $http({
        url: settings.oortUrl + '/search',
        params: angular.extend(defaultParams, params)
      });
    }

    return {
      search: search
    };
  });
