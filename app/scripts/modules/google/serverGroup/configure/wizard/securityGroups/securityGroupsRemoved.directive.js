'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.google.serverGroup.configure.wizard.securityGroups.removed.directive', [
  ])
  .directive('gceServerGroupSecurityGroupsRemoved', function () {
    return {
      restrict: 'E',
      templateUrl: require('./securityGroupsRemoved.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
      },
      controllerAs: 'vm',
      controller: 'gceServerGroupSecurityGroupsRemovedCtrl',
    };
  }).controller('gceServerGroupSecurityGroupsRemovedCtrl', function () {
    this.acknowledgeSecurityGroupRemoval = () => {
      this.command.viewState.dirty.securityGroups = null;
    };
  });
