'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.search.searchResult.directive', [
  ])
  .directive('searchResult', function () {
    return {
      restrict: 'E',
      templateUrl: require('./searchResult.directive.html'),
      scope: {
        item: '='
      },
    };
  });



