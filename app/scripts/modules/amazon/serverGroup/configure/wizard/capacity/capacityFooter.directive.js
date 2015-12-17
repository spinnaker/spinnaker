'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.amazon.serverGroup.configure.wizard.capacity.footer.directive', [
  ])
  .directive('awsServerGroupCapacityFooter', function () {
    return {
      restrict: 'E',
      templateUrl: require('./capacityFooter.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
        wizard: '=',
        form: '=',
        taskMonitor: '=',
        cancel: '&',
        isValid: '&',
        showSubmitButton: '&',
        submit: '&'
      },
      controllerAs: 'vm',
      controller: angular.noop,
    };
  });
