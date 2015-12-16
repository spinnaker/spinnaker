'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.preconditions.config', [])
  .provider('preconditionTypeConfig', function() {

    var preconditionTypes = [];

    function registerPreconditionType(config) {
      preconditionTypes.push(config);
    }

    function listPreconditionTypes() {
      return angular.copy(preconditionTypes);
    }

    this.registerPreconditionType = registerPreconditionType;

    this.$get = function() {
      return {
        listPreconditionTypes: listPreconditionTypes
      };
    };

  }
);
