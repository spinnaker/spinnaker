'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.google.serverGroup.configure.wizard.capacity.regional.directive', [
  ])
  .directive('gceRegionalSelector', function () {
    return {
      restrict: 'E',
      templateUrl: require('./regionalSelector.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
      },
      controllerAs: 'vm',
      controller: angular.noop,
    };
  });
