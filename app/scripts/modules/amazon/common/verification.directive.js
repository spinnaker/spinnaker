'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.amazon.verification.directive', [
  ])
  .directive('awsVerification', function () {
    return {
      restrict: 'E',
      templateUrl: require('./verification.directive.html'),
      scope: {},
      bindToController: {
        verification: '=',
        account: '='
      },
      controllerAs: 'vm',
      controller: angular.noop,
    };
  });
