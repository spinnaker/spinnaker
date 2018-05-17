'use strict';

const angular = require('angular');

import { PIPELINE_BAKE_STAGE_CHOOSE_OS } from 'core/pipeline/config/stages/bake/bakeStageChooseOs.component';

module.exports = angular.module('spinnaker.core.pipeline.stage.bake', [
  require('./bakeStage').name,
  require('./modal/addExtendedAttribute.controller.modal').name,
  PIPELINE_BAKE_STAGE_CHOOSE_OS,
]);
