'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.amazon.serverGroup.configure.wizard.securityGroups.footer.directive', [
  ])
  .directive('awsServerGroupSecurityGroupsFooter', function () {
    return {
      restrict: 'E',
      templateUrl: require('./securityGroupsFooter.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
        wizard: '=',
        taskMonitor: '=',
        cancel: '&',
        isValid: '&',
        showSubmitButton: '&',
        submit: '&',
      },
      controllerAs: 'vm',
      controller: angular.noop,
    };
  });
