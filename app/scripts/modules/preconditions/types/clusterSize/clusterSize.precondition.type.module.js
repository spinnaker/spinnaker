'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.precondition.types.clusterSize', [])
  .config(function(preconditionTypeConfigProvider) {
    preconditionTypeConfigProvider.registerPreconditionType({
      label: 'Cluster Size',
      key: 'clusterSize',
      contextTemplateUrl: 'app/scripts/modules/preconditions/types/clusterSize/additionalFields.html',
    });
  }).name;
