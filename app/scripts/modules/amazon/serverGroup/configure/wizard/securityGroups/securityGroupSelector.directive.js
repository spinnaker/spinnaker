'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.amazon.serverGroup.configure.wizard.securityGroups.selector.directive', [
    require('core/cache/infrastructureCaches.js'),
    require('../../serverGroupConfiguration.service.js'),
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
    this.getSecurityGroupRefreshTime = () => {
      return infrastructureCaches.securityGroups.getStats().ageMax;
    };

    this.refreshSecurityGroups = () => {
      this.refreshing = true;
      if (this.refresh) {
        this.refresh().then(() => this.refreshing = false);
      } else {
        awsServerGroupConfigurationService.refreshSecurityGroups(this.command).then(() => {
          this.refreshing = false;
        });
      }
    };
  });
