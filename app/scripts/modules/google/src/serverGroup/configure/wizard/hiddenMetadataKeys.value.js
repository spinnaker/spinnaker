'use strict';

import { module } from 'angular';

export const GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_HIDDENMETADATAKEYS_VALUE =
  'spinnaker.deck.gce.serverGroup.hiddenMetadataKeys.value';
export const name = GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_HIDDENMETADATAKEYS_VALUE; // for backwards compatibility
module(GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_HIDDENMETADATAKEYS_VALUE, []).value('gceServerGroupHiddenMetadataKeys', [
  'load-balancer-names',
  'global-load-balancer-names',
  'backend-service-names',
  'load-balancing-policy',
  'select-zones',
  'customUserData',
]);
