'use strict';

const angular = require('angular');

import { InfrastructureCaches } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.google.serverGroup.configure.wizard.securityGroups.selector.directive', [
    require('../../serverGroupConfiguration.service').name,
    require('./tagSelectorGenerator.component').name,
    require('./tagManager.service').name,
  ])
  .directive('gceServerGroupSecurityGroupSelector', function() {
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
  })
  .controller('gceServerGroupSecurityGroupsSelectorCtrl', function(gceServerGroupConfigurationService, gceTagManager) {
    this.getSecurityGroupRefreshTime = () => {
      return InfrastructureCaches.get('securityGroups').getStats().ageMax;
    };

    this.refreshSecurityGroups = () => {
      this.refreshing = true;
      gceServerGroupConfigurationService.refreshSecurityGroups(this.command).then(() => {
        this.refreshing = false;
      });
    };

    this.onRemove = gceTagManager.removeSecurityGroup;
  });
