'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.config.preconditions.types.clusterSize', [])
  .config(['preconditionTypeConfigProvider', function(preconditionTypeConfigProvider) {
    preconditionTypeConfigProvider.registerPreconditionType({
      label: 'Cluster Size',
      key: 'clusterSize',
      contextTemplateUrl: require('./additionalFields.html'),
    });
  }]);
