'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.config.preconditions.types.expression', [])
  .config(['preconditionTypeConfigProvider', function(preconditionTypeConfigProvider) {
    preconditionTypeConfigProvider.registerPreconditionType({
      label: 'Expression',
      key: 'expression',
      contextTemplateUrl: require('./additionalFields.html'),
    });
  }]);
