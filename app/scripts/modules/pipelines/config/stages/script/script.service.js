'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.script.service', [])
  .factory('scriptService', function($q) {

    function getCredentials() {
      return $q.when(['spinnaker']);
    }

    return {
      getCredentials: getCredentials,
    };
  }).name;
