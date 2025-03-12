'use strict';

import { module } from 'angular';

import { InfrastructureCaches } from '@spinnaker/core';

import { GOOGLE_SERVERGROUP_CONFIGURE_SERVERGROUPCONFIGURATION_SERVICE } from '../../serverGroupConfiguration.service';
import { GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGMANAGER_SERVICE } from './tagManager.service';
import { GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGSELECTORGENERATOR_COMPONENT } from './tagSelectorGenerator.component';

export const GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_SECURITYGROUPSELECTOR_DIRECTIVE =
  'spinnaker.google.serverGroup.configure.wizard.securityGroups.selector.directive';
export const name = GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_SECURITYGROUPSELECTOR_DIRECTIVE; // for backwards compatibility
module(GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_SECURITYGROUPSELECTOR_DIRECTIVE, [
  GOOGLE_SERVERGROUP_CONFIGURE_SERVERGROUPCONFIGURATION_SERVICE,
  GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGSELECTORGENERATOR_COMPONENT,
  GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGMANAGER_SERVICE,
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
  })
  .controller('gceServerGroupSecurityGroupsSelectorCtrl', [
    'gceServerGroupConfigurationService',
    'gceTagManager',
    function (gceServerGroupConfigurationService, gceTagManager) {
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
