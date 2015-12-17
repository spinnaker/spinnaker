'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.help.registry', [])
  .factory('helpContentsRegistry', function () {
    const helpFields = {};

    function getHelpField(key) {
      return helpFields[key] || null;
    }

    function register(key, val) {
      helpFields[key] = val;
    }

    return {
      getHelpField: getHelpField,
      register: register
    };
  });
