'use strict';

import { module } from 'angular';

import { GCE_ADD_AUTOHEALING_POLICY_BUTTON } from './autoHealingPolicy/addAutoHealingPolicyButton.component';
import { GCE_AUTOHEALING_POLICY_DETAILS } from './autoHealingPolicy/autoHealingPolicy.component';
import { GCE_UPSERT_AUTOHEALING_POLICY_MODAL_CTRL } from './autoHealingPolicy/modal/upsertAutoHealingPolicy.modal.controller';
import { GOOGLE_SERVERGROUP_DETAILS_SERVERGROUPDETAILS_GCE_CONTROLLER } from './serverGroupDetails.gce.controller';

export const GOOGLE_SERVERGROUP_DETAILS_SERVERGROUP_DETAILS_GCE_MODULE = 'spinnaker.serverGroup.details.gce';
export const name = GOOGLE_SERVERGROUP_DETAILS_SERVERGROUP_DETAILS_GCE_MODULE; // for backwards compatibility
module(GOOGLE_SERVERGROUP_DETAILS_SERVERGROUP_DETAILS_GCE_MODULE, [
  GOOGLE_SERVERGROUP_DETAILS_SERVERGROUPDETAILS_GCE_CONTROLLER,
  GCE_ADD_AUTOHEALING_POLICY_BUTTON,
  GCE_UPSERT_AUTOHEALING_POLICY_MODAL_CTRL,
  GCE_AUTOHEALING_POLICY_DETAILS,
]);
