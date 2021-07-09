'use strict';

import { module } from 'angular';

export const GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_SECURITYGROUPSREMOVED_DIRECTIVE =
  'spinnaker.google.serverGroup.configure.wizard.securityGroups.removed.directive';
export const name = GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_SECURITYGROUPSREMOVED_DIRECTIVE; // for backwards compatibility
module(GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_SECURITYGROUPSREMOVED_DIRECTIVE, [])
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
  })
  .controller('gceServerGroupSecurityGroupsRemovedCtrl', function () {
    this.acknowledgeSecurityGroupRemoval = () => {
      this.command.viewState.dirty.securityGroups = null;
    };
  });
