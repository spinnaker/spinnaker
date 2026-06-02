'use strict';

export const GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_HIDDENMETADATAKEYS_VALUE =
  'spinnaker.deck.gce.serverGroup.hiddenMetadataKeys.value';
export const name = GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_HIDDENMETADATAKEYS_VALUE; // for backwards compatibility
export const GCE_SERVER_GROUP_HIDDEN_METADATA_KEYS = [
  'load-balancer-names',
  'global-load-balancer-names',
  'backend-service-names',
  'load-balancing-policy',
  'select-zones',
  'customUserData',
];
