'use strict';

const angular = require('angular');

import { INFRASTRUCTURE_CACHE_SERVICE } from '@spinnaker/core';

import { AWS_SERVER_GROUP_CONFIGURATION_SERVICE } from 'amazon/serverGroup/configure/serverGroupConfiguration.service';

module.exports = angular
  .module('spinnaker.amazon.serverGroup.configure.wizard.securityGroups.selector.directive', [
    INFRASTRUCTURE_CACHE_SERVICE,
    AWS_SERVER_GROUP_CONFIGURATION_SERVICE,
  ])
  .directive('serverGroupSecurityGroupSelector', function () {
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
        helpKey: '@'
      },
      controllerAs: 'vm',
      controller: 'awsServerGroupSecurityGroupsSelectorCtrl',
    };
  }).controller('awsServerGroupSecurityGroupsSelectorCtrl', function (awsServerGroupConfigurationService, infrastructureCaches) {

    let setSecurityGroupRefreshTime = () => {
      this.refreshTime = infrastructureCaches.get('securityGroups').getStats().ageMax;
    };

    this.addItems = () => this.currentItems += 25;

    this.resetCurrentItems = () => this.currentItems = 25;

    this.refreshSecurityGroups = () => {
      this.refreshing = true;
      if (this.refresh) {
        this.refresh().then(() => this.refreshing = false);
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
