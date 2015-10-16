'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.preconditions.types.clusterSize', [])
  .config(function(preconditionTypeConfigProvider) {
    preconditionTypeConfigProvider.registerPreconditionType({
      label: 'Cluster Size',
      key: 'clusterSize',
      contextTemplateUrl: 'app/scripts/modules/core/pipeline/config/preconditions/types/clusterSize/additionalFields.html',
    });
  }).name;
