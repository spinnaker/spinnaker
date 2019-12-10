'use strict';

const angular = require('angular');

import { CREATE_PIPELINE_COMPONENT } from './createPipeline.component';
import { PIPELINE_GRAPH_COMPONENT } from './graph/pipeline.graph.component';
import { TARGET_SELECT_COMPONENT } from 'core/pipeline/config/targetSelect.component';
import { TRIGGERS } from './triggers/triggers.module';
import './triggers';
import './validation/requiredField.validator';
import './validation/anyFieldRequired.validator';
import './validation/serviceAccountAccess.validator';
import './validation/stageBeforeType.validator';
import './validation/stageOrTriggerBeforeType.validator';
import './validation/upstreamVersionProvided.validator';
import './validation/targetImpedance.validator';

import './pipelineConfig.less';

export const CORE_PIPELINE_CONFIG_PIPELINECONFIG_MODULE = 'spinnaker.core.pipeline.config';
export const name = CORE_PIPELINE_CONFIG_PIPELINECONFIG_MODULE; // for backwards compatibility
angular.module(CORE_PIPELINE_CONFIG_PIPELINECONFIG_MODULE, [
  CREATE_PIPELINE_COMPONENT,
  PIPELINE_GRAPH_COMPONENT,
  require('./stages/stage.module').name,
  require('./stages/baseProviderStage/baseProviderStage').name,
  TRIGGERS,
  require('./parameters/pipeline.module').name,
  require('./pipelineConfig.controller').name,
  require('./pipelineConfigView').name,
  require('./pipelineConfigurer').name,
  TARGET_SELECT_COMPONENT,
  require('./health/stagePlatformHealthOverride.directive').name,
]);
