'use strict';

const angular = require('angular');

import { CREATE_PIPELINE_COMPONENT } from './createPipeline.component';
import { PIPELINE_GRAPH_COMPONENT } from './graph/pipeline.graph.component';
import { TARGET_SELECT_COMPONENT } from 'core/pipeline/config/targetSelect.component';
import './validation/requiredField.validator';
import './validation/anyFieldRequired.validator';
import './validation/serviceAccountAccess.validator';
import './validation/stageBeforeType.validator';
import './validation/stageOrTriggerBeforeType.validator';
import './validation/upstreamVersionProvided.validator';
import './validation/targetImpedance.validator';

import './pipelineConfig.less';

module.exports = angular.module('spinnaker.core.pipeline.config', [
  CREATE_PIPELINE_COMPONENT,
  require('./actions/actions.module.js').name,
  PIPELINE_GRAPH_COMPONENT,
  require('./stages/stage.module.js').name,
  require('./stages/baseProviderStage/baseProviderStage.js').name,
  require('./triggers/trigger.module.js').name,
  require('./parameters/pipeline.module.js').name,
  require('./pipelineConfig.controller.js').name,
  require('./pipelineConfigView.js').name,
  require('./pipelineConfigurer.js').name,
  TARGET_SELECT_COMPONENT,
  require('./health/stagePlatformHealthOverride.directive.js').name,
]);
