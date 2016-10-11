'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.google.serverGroup.configure.wizard.securityGroups.selector.directive', [
    require('core/cache/infrastructureCaches.js'),
    require('../../serverGroupConfiguration.service.js'),
    require('./tagSelectorGenerator.component.js'),
    require('./tagManager.service.js'),
  ])
  .directive('gceServerGroupSecurityGroupSelector', function () {
    return {
      restrict: 'E',
      templateUrl: require('./securityGroupSelector.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
      },
      controllerAs: 'vm',
      controller: 'gceServerGroupSecurityGroupsSelectorCtrl',
    };
  }).controller('gceServerGroupSecurityGroupsSelectorCtrl', function (gceServerGroupConfigurationService,
                                                                      gceTagManager,
                                                                      infrastructureCaches) {
    this.getSecurityGroupRefreshTime = () => {
      return infrastructureCaches.securityGroups.getStats().ageMax;
    };

    this.refreshSecurityGroups = () => {
      this.refreshing = true;
      gceServerGroupConfigurationService.refreshSecurityGroups(this.command).then(() => {
        this.refreshing = false;
      });
    };

    this.onRemove = gceTagManager.removeSecurityGroup;
  });
