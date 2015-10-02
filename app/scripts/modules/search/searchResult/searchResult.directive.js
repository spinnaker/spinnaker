'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.search.searchResult.directive', [
    require('../../core/account/accountTag.directive.js')
  ])
  .directive('searchResult', function () {
    return {
      restrict: 'E',
      templateUrl: require('./searchResult.directive.html'),
      scope: {
        item: '='
      },
    };
  })
  .name;



