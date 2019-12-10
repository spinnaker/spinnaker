'use strict';

import { module } from 'angular';

export const ECS_SERVERGROUP_CONFIGURE_WIZARD_ADVANCEDSETTINGS_ADVANCEDSETTINGS_COMPONENT =
  'spinnaker.ecs.serverGroup.configure.wizard.advancedSettings.component';
export const name = ECS_SERVERGROUP_CONFIGURE_WIZARD_ADVANCEDSETTINGS_ADVANCEDSETTINGS_COMPONENT; // for backwards compatibility
module(ECS_SERVERGROUP_CONFIGURE_WIZARD_ADVANCEDSETTINGS_ADVANCEDSETTINGS_COMPONENT, []).component(
  'ecsServerGroupAdvancedSettings',
  {
    bindings: {
      command: '=',
      application: '=',
    },
    templateUrl: require('./advancedSettings.component.html'),
  },
);
