'use strict';

import { module } from 'angular';

export const CORE_SERVERGROUP_CONFIGURE_COMMON_DIRTYINSTANCETYPENOTIFICATION_COMPONENT =
  'spinnaker.deck.core.serverGroup.dirtyInstanceTypeNotification.component';
export const name = CORE_SERVERGROUP_CONFIGURE_COMMON_DIRTYINSTANCETYPENOTIFICATION_COMPONENT; // for backwards compatibility
module(CORE_SERVERGROUP_CONFIGURE_COMMON_DIRTYINSTANCETYPENOTIFICATION_COMPONENT, []).component(
  'dirtyInstanceTypeNotification',
  {
    bindings: {
      command: '=',
    },
    templateUrl: require('./dirtyInstanceTypeNotification.component.html'),
  },
);
