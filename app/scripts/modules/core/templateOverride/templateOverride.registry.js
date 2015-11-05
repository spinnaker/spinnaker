'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.templateOverride.registry', [
])
  .provider('templateOverrideRegistry', function() {

    const overrides = Object.create(null);

    this.override = (key, val) => {
      overrides[key] = val;
    };

    function getTemplate(key, defaultVal) {
      return overrides[key] || defaultVal;
    }

    this.$get = function() {
      return {
        getTemplate: getTemplate,
      };
    };

  }).name;
