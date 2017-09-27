'use strict';

const angular = require('angular');

import { PIPELINE_GRAPH_COMPONENT } from './graph/pipeline.graph.component';
import { REQUIRED_FIELD_VALIDATOR } from './validation/requiredField.validator';
import { SERVICE_ACCOUNT_ACCESS_VALIDATOR } from './validation/serviceAccountAccess.validator';
import { STAGE_BEFORE_TYPE_VALIDATOR } from './validation/stageBeforeType.validator';
import { STAGE_OR_TRIGGER_BEFORE_TYPE_VALIDATOR } from './validation/stageOrTriggerBeforeType.validator';
import { TARGET_IMPEDANCE_VALIDATOR } from './validation/targetImpedance.validator';

import './pipelineConfig.less';

module.exports = angular.module('spinnaker.core.pipeline.config', [
  require('./actions/actions.module.js').name,
  PIPELINE_GRAPH_COMPONENT,
  require('./stages/stage.module.js').name,
  require('./stages/baseProviderStage/baseProviderStage.js').name,
  require('./triggers/trigger.module.js').name,
  require('./parameters/pipeline.module.js').name,
  require('./pipelineConfig.controller.js').name,
  require('./pipelineConfigView.js').name,
  require('./pipelineConfigurer.js').name,
  REQUIRED_FIELD_VALIDATOR,
  TARGET_IMPEDANCE_VALIDATOR,
  STAGE_OR_TRIGGER_BEFORE_TYPE_VALIDATOR,
  STAGE_BEFORE_TYPE_VALIDATOR,
  SERVICE_ACCOUNT_ACCESS_VALIDATOR,
  require('./targetSelect.directive.js').name,
  require('./createNew.directive.js').name,
  require('./health/stagePlatformHealthOverride.directive.js').name,
]);
