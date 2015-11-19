'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.templateOverride.registry', [])
  .factory('templateOverrideRegistry', function() {

    const overrides = Object.create(null);

    function override (key, val) {
      overrides[key] = val;
    }

    function getTemplate(key, defaultVal) {
      return overrides[key] || defaultVal;
    }

    return {
      getTemplate: getTemplate,
      override: override,
    };

  }).name;
