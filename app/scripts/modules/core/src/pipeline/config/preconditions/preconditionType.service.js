'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.config.preconditions.service', [require('./preconditionTypeConfig.provider').name])
  .factory('preconditionTypeService', function(preconditionTypeConfig) {
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
  });
