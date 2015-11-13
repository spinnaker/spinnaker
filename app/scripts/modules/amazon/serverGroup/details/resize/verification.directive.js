'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.amazon.serverGroup.details.resize.verification.directive', [
  ])
  .directive('awsResizeVerification', function () {
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
  })
  .name;
