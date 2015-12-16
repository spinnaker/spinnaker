'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.filterModel.filterTags.directive', [
  ])
  .directive('filterTags', function () {
    return {
      restrict: 'E',
      templateUrl: require('./filter.tags.html'),
      scope: {
        tags: '=',
        tagCleared: '&',
        clearFilters: '&',
      },
    };
  });
