'use strict';

import {has} from 'lodash';

const angular = require('angular');

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
        removed: '=',
      },
      controllerAs: 'vm',
      controller: 'awsServerGroupSecurityGroupsRemovedCtrl',
    };
  }).controller('awsServerGroupSecurityGroupsRemovedCtrl', function () {
    this.acknowledgeSecurityGroupRemoval = () => {
      if (has(this.command, 'viewState.dirty')) {
        this.command.viewState.dirty.securityGroups = null;
      }
      if (this.removed && this.removed.length) {
        this.removed.length = 0;
      }
    };
  });
