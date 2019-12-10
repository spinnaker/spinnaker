'use strict';

const angular = require('angular');

export const CORE_PIPELINE_CONFIG_STAGES_FAILONFAILEDEXPRESSIONS_FAILONFAILEDEXPRESSIONS_DIRECTIVE =
  'spinnaker.core.pipeline.stage.failOnFailedExpressions.directive';
export const name = CORE_PIPELINE_CONFIG_STAGES_FAILONFAILEDEXPRESSIONS_FAILONFAILEDEXPRESSIONS_DIRECTIVE; // for backwards compatibility
angular
  .module(CORE_PIPELINE_CONFIG_STAGES_FAILONFAILEDEXPRESSIONS_FAILONFAILEDEXPRESSIONS_DIRECTIVE, [])
  .component('failOnFailedExpressions', {
    bindings: {
      stage: '<',
    },
    templateUrl: require('./failOnFailedExpressions.directive.html'),
  });
