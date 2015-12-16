'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.preconditions.types.expression', [])
    .config(function (preconditionTypeConfigProvider) {
      preconditionTypeConfigProvider.registerPreconditionType({
        label: 'Expression',
        key: 'expression',
        contextTemplateUrl: require('./additionalFields.html'),
      });
    });
