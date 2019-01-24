'use strict';

import { GCE_ADD_AUTOHEALING_POLICY_BUTTON } from './autoHealingPolicy/addAutoHealingPolicyButton.component';
import { GCE_AUTOHEALING_POLICY_DETAILS } from './autoHealingPolicy/autoHealingPolicy.component';
import { GCE_UPSERT_AUTOHEALING_POLICY_MODAL_CTRL } from './autoHealingPolicy/modal/upsertAutoHealingPolicy.modal.controller';

const angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.details.gce', [
  require('./serverGroupDetails.gce.controller').name,
  GCE_ADD_AUTOHEALING_POLICY_BUTTON,
  GCE_UPSERT_AUTOHEALING_POLICY_MODAL_CTRL,
  GCE_AUTOHEALING_POLICY_DETAILS,
]);
