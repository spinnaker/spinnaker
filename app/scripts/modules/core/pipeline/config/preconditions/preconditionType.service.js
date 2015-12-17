'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.preconditions.service', [
  require('./preconditionTypeConfig.provider.js'),
])
  .factory('preconditionTypeService', function (preconditionTypeConfig, _) {

    function listPreconditionTypes() {
      return preconditionTypeConfig.listPreconditionTypes();
    }

    function getPreconditionType(key) {
      return  _.find(preconditionTypeConfig.listPreconditionTypes(), { key: key });
    }

    return {
      listPreconditionTypes: listPreconditionTypes,
      getPreconditionType: getPreconditionType
    };

  });
