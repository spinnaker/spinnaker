'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.override.registry', [])
  .factory('overrideRegistry', function() {

    const templateOverrides = Object.create(null);
    const controllerOverrides = Object.create(null);

    function overrideTemplate (key, val) {
      templateOverrides[key] = val;
    }

    function overrideController (key, val) {
      controllerOverrides[key] = val;
    }

    function getTemplate(key, defaultVal) {
      return templateOverrides[key] || defaultVal;
    }

    function getController(key) {
      return controllerOverrides[key] || key;
    }

    return {
      getTemplate: getTemplate,
      getController: getController,
      overrideTemplate: overrideTemplate,
      overrideController: overrideController
    };

  });
