'use strict';

import * as angular from 'angular';

export const CORE_PIPELINE_CONFIG_PRECONDITIONS_PRECONDITIONTYPECONFIG_PROVIDER =
  'spinnaker.core.pipeline.config.preconditions.config';
export const name = CORE_PIPELINE_CONFIG_PRECONDITIONS_PRECONDITIONTYPECONFIG_PROVIDER; // for backwards compatibility
angular
  .module(CORE_PIPELINE_CONFIG_PRECONDITIONS_PRECONDITIONTYPECONFIG_PROVIDER, [])
  .provider('preconditionTypeConfig', function () {
    const preconditionTypes = [];

    function registerPreconditionType(config) {
      preconditionTypes.push(config);
    }

    function listPreconditionTypes() {
      return angular.copy(preconditionTypes);
    }

    this.registerPreconditionType = registerPreconditionType;

    this.$get = function () {
      return {
        listPreconditionTypes: listPreconditionTypes,
      };
    };
  });
