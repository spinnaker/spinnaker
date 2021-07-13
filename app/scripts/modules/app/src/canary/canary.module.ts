import { module } from 'angular';

import { CANARY_ACATASK_ACATASKSTAGE_MODULE } from './acaTask/acaTaskStage.module';
import './canary.help';
import { CANARY_CANARY_CANARYSTAGE_MODULE } from './canary/canaryStage.module';

import './canary.less';

/*! Start - Rollup Remove */
// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function (key) {
  templates(key);
});
/*! End - Rollup Remove */

export const CANARY_MODULE = 'spinnaker.canary';
module(CANARY_MODULE, [CANARY_ACATASK_ACATASKSTAGE_MODULE, CANARY_CANARY_CANARYSTAGE_MODULE]);
