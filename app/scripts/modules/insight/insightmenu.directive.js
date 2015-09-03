'use strict';

/**
 * @ngdoc directive
 * @name spinnaker.directive:insightMenu
 * @description
 * # insightMenu
 */

let angular = require('angular');

module.exports = angular.module('spinnaker.insightMenu.directive', [])
  .directive('insightMenu', function () {
    return {
      templateUrl: require('./insightMenu.directive.html'),
      restrict: 'E',
      replace: true,
      scope: {
        actions: '=',
        title: '@',
        icon: '@',
        rightAlign: '&',
      },
    };
  }).name;
