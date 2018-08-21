'use strict';

const angular = require('angular');

import { InfrastructureCaches } from '@spinnaker/core';

import { AWS_SERVER_GROUP_CONFIGURATION_SERVICE } from '@spinnaker/amazon';

module.exports = angular
  .module('spinnaker.amazon.serverGroup.configure.wizard.securityGroups.selector.directive', [
    AWS_SERVER_GROUP_CONFIGURATION_SERVICE,
  ])
  .directive('serverGroupSecurityGroupSelector', function() {
    return {
      restrict: 'E',
      templateUrl: require('./securityGroupSelector.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
        availableGroups: '<',
        hideLabel: '<',
        refresh: '&?',
        groupsToEdit: '=',
        helpKey: '@',
      },
      controllerAs: 'vm',
      controller: 'awsServerGroupSecurityGroupsSelectorCtrl',
    };
  })
  .controller('awsServerGroupSecurityGroupsSelectorCtrl', function(awsServerGroupConfigurationService) {
    let setSecurityGroupRefreshTime = () => {
      this.refreshTime = InfrastructureCaches.get('securityGroups').getStats().ageMax;
    };

    this.addItems = () => (this.currentItems += 25);

    this.resetCurrentItems = () => (this.currentItems = 25);

    this.refreshSecurityGroups = () => {
      this.refreshing = true;
      if (this.refresh) {
        this.refresh().then(() => (this.refreshing = false));
      } else {
        awsServerGroupConfigurationService.refreshSecurityGroups(this.command).then(() => {
          this.refreshing = false;
          setSecurityGroupRefreshTime();
        });
      }
    };

    this.currentItems = 25;
    setSecurityGroupRefreshTime();
  });
