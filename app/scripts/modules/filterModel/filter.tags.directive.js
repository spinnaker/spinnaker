'use strict';

let angular = require('angular');

require('./filter.tags.html');

module.exports = angular
  .module('spinnaker.filterModel.filterTags.directive', [
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
  })
  .name;
