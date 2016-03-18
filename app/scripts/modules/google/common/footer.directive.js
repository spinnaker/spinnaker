'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.google.footer.directive', [
  ])
  .directive('gceFooter', function () {
    return {
      restrict: 'E',
      templateUrl: require('./footer.directive.html'),
      scope: {},
      bindToController: {
        action: '&',
        isValid: '&',
        cancel: '&',
        account: '=?',
        verification: '=?',
      },
      controllerAs: 'vm',
      controller: angular.noop,
    };
  });
