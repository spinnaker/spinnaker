'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.determineTargetReference', [
 require('./determineTargetReference.js')
])
.name;
