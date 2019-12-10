'use strict';

const angular = require('angular');

export const CORE_PIPELINE_CONFIG_STAGES_PIPELINE_PIPELINESTAGE_MODULE = 'spinnaker.core.pipeline.stage.pipeline';
export const name = CORE_PIPELINE_CONFIG_STAGES_PIPELINE_PIPELINESTAGE_MODULE; // for backwards compatibility
angular.module(CORE_PIPELINE_CONFIG_STAGES_PIPELINE_PIPELINESTAGE_MODULE, [require('./pipelineStage').name]);
