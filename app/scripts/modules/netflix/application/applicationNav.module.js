'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.application.applicationNav', [])
  .run(function($templateCache) {
    $templateCache.put(
      require('../../core/application/applicationNav.html'),
      $templateCache.get(require('./applicationNav.html'))
    );
  }).name;
