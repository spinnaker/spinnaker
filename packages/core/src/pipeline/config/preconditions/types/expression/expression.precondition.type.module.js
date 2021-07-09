'use strict';

import { module } from 'angular';

export const CORE_PIPELINE_CONFIG_PRECONDITIONS_TYPES_EXPRESSION_EXPRESSION_PRECONDITION_TYPE_MODULE =
  'spinnaker.core.pipeline.config.preconditions.types.expression';
export const name = CORE_PIPELINE_CONFIG_PRECONDITIONS_TYPES_EXPRESSION_EXPRESSION_PRECONDITION_TYPE_MODULE; // for backwards compatibility
module(CORE_PIPELINE_CONFIG_PRECONDITIONS_TYPES_EXPRESSION_EXPRESSION_PRECONDITION_TYPE_MODULE, []).config([
  'preconditionTypeConfigProvider',
  function (preconditionTypeConfigProvider) {
    preconditionTypeConfigProvider.registerPreconditionType({
      label: 'Expression',
      key: 'expression',
      contextTemplateUrl: require('./additionalFields.html'),
    });
  },
]);
