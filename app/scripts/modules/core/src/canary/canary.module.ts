import { module } from 'angular';

import { CANARY_HELP } from './canary.help';
import { CANARY_SCORES_CONFIG_COMPONENT } from './canaryScores.component';

import './canary.less';

// This module exists to facilitate sharing common components between canary UI implementations.
export const CANARY_MODULE = 'spinnaker.core.canary.module';
module(CANARY_MODULE, [
  CANARY_HELP,
  CANARY_SCORES_CONFIG_COMPONENT,
]);
