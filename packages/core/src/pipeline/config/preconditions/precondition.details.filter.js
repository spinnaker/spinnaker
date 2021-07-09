'use strict';

import { module } from 'angular';

export const CORE_PIPELINE_CONFIG_PRECONDITIONS_PRECONDITION_DETAILS_FILTER =
  'spinnaker.core.pipeline.config.preconditions.details.filter';
export const name = CORE_PIPELINE_CONFIG_PRECONDITIONS_PRECONDITION_DETAILS_FILTER; // for backwards compatibility
module(CORE_PIPELINE_CONFIG_PRECONDITIONS_PRECONDITION_DETAILS_FILTER, []).filter('preconditionType', function () {
  return function (input) {
    return input.charAt(0).toUpperCase() + input.slice(1);
  };
});
