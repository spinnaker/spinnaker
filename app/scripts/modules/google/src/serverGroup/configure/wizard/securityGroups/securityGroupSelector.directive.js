'use strict';

const angular = require('angular');

import { InfrastructureCaches } from '@spinnaker/core';

export const GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_SECURITYGROUPSELECTOR_DIRECTIVE =
  'spinnaker.google.serverGroup.configure.wizard.securityGroups.selector.directive';
export const name = GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_SECURITYGROUPSELECTOR_DIRECTIVE; // for backwards compatibility
angular
  .module(GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_SECURITYGROUPSELECTOR_DIRECTIVE, [
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
  .controller('gceServerGroupSecurityGroupsSelectorCtrl', [
    'gceServerGroupConfigurationService',
    'gceTagManager',
    function(gceServerGroupConfigurationService, gceTagManager) {
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
    },
  ]);
