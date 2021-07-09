import { module } from 'angular';

import { CORE_PIPELINE_CONFIG_PRECONDITIONS_PRECONDITIONTYPECONFIG_PROVIDER } from './preconditionTypeConfig.provider';

('use strict');

export const CORE_PIPELINE_CONFIG_PRECONDITIONS_PRECONDITIONTYPE_SERVICE =
  'spinnaker.core.pipeline.config.preconditions.service';
export const name = CORE_PIPELINE_CONFIG_PRECONDITIONS_PRECONDITIONTYPE_SERVICE; // for backwards compatibility
module(CORE_PIPELINE_CONFIG_PRECONDITIONS_PRECONDITIONTYPE_SERVICE, [
  CORE_PIPELINE_CONFIG_PRECONDITIONS_PRECONDITIONTYPECONFIG_PROVIDER,
]).factory('preconditionTypeService', [
  'preconditionTypeConfig',
  function (preconditionTypeConfig) {
    function listPreconditionTypes() {
      return preconditionTypeConfig.listPreconditionTypes();
    }

    function getPreconditionType(key) {
      return _.find(preconditionTypeConfig.listPreconditionTypes(), { key: key });
    }

    return {
      listPreconditionTypes: listPreconditionTypes,
      getPreconditionType: getPreconditionType,
    };
  },
]);
