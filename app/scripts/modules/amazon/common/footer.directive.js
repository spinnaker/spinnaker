'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.amazon.footer.directive', [
  ])
  .directive('awsFooter', function () {
    return {
      restrict: 'E',
      templateUrl: require('./footer.directive.html'),
      scope: {},
      bindToController: {
        action: '&',
        isValid: '&',
        cancel: '&',
      },
      controllerAs: 'vm',
      controller: angular.noop,
    };
  });
