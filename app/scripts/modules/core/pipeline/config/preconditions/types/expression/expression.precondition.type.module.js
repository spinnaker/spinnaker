'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.preconditions.types.expression', [])
    .config(function(preconditionTypeConfigProvider) {
        preconditionTypeConfigProvider.registerPreconditionType({
            label: 'Expression',
            key: 'expression',
            contextTemplateUrl: 'app/scripts/modules/core/pipeline/config/preconditions/types/expression/additionalFields.html',
        });
    }).name;
