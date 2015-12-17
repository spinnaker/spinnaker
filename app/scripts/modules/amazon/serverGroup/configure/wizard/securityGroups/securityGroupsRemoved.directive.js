'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.amazon.serverGroup.configure.wizard.securityGroups.removed.directive', [
  ])
  .directive('serverGroupSecurityGroupsRemoved', function () {
    return {
      restrict: 'E',
      templateUrl: require('./securityGroupsRemoved.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
      },
      controllerAs: 'vm',
      controller: 'awsServerGroupSecurityGroupsRemovedCtrl',
    };
  }).controller('awsServerGroupSecurityGroupsRemovedCtrl', function () {
    this.acknowledgeSecurityGroupRemoval = () => {
      this.command.viewState.dirty.securityGroups = null;
    };
  });
