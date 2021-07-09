'use strict';

import { module } from 'angular';

export const GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_CAPACITY_ADVANCEDCAPACITYSELECTOR_COMPONENT =
  'spinnaker.deck.gce.serverGroup.configure.advancedCapacitySelector.component';
export const name = GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_CAPACITY_ADVANCEDCAPACITYSELECTOR_COMPONENT; // for backwards compatibility
module(GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_CAPACITY_ADVANCEDCAPACITYSELECTOR_COMPONENT, []).component(
  'gceServerGroupAdvancedCapacitySelector',
  {
    bindings: {
      command: '=',
    },
    templateUrl: require('./advancedCapacitySelector.component.html'),
  },
);
