'use strict';

import { module } from 'angular';

export const ECS_SERVERGROUP_CONFIGURE_WIZARD_HORIZONTALSCALING_HORIZONTALSCALING_COMPONENT =
  'spinnaker.ecs.serverGroup.configure.wizard.horizontalScaling.component';
export const name = ECS_SERVERGROUP_CONFIGURE_WIZARD_HORIZONTALSCALING_HORIZONTALSCALING_COMPONENT; // for backwards compatibility
module(ECS_SERVERGROUP_CONFIGURE_WIZARD_HORIZONTALSCALING_HORIZONTALSCALING_COMPONENT, []).component(
  'ecsServerGroupHorizontalScaling',
  {
    bindings: {
      command: '=',
      application: '=',
      capacityProviderState: '=',
      notifyAngular: '=',
      configureCommand: '=',
    },
    templateUrl: require('./horizontalScaling.component.html'),
  },
);
