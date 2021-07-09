'use strict';

import { module } from 'angular';

export const CORE_PIPELINE_CONFIG_PRECONDITIONS_TYPES_CLUSTERSIZE_CLUSTERSIZE_PRECONDITION_TYPE_MODULE =
  'spinnaker.core.pipeline.config.preconditions.types.clusterSize';
export const name = CORE_PIPELINE_CONFIG_PRECONDITIONS_TYPES_CLUSTERSIZE_CLUSTERSIZE_PRECONDITION_TYPE_MODULE; // for backwards compatibility
module(CORE_PIPELINE_CONFIG_PRECONDITIONS_TYPES_CLUSTERSIZE_CLUSTERSIZE_PRECONDITION_TYPE_MODULE, []).config([
  'preconditionTypeConfigProvider',
  function (preconditionTypeConfigProvider) {
    preconditionTypeConfigProvider.registerPreconditionType({
      label: 'Cluster Size',
      key: 'clusterSize',
      contextTemplateUrl: require('./additionalFields.html'),
    });
  },
]);
