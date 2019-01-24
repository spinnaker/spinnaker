'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.runJob', [require('./runJobStage').name]);
