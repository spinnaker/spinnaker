'use strict';

import { Registry } from 'core/registry';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.runJobStage', []).config(function() {
  Registry.pipeline.registerStage({
    useBaseProvider: true,
    key: 'runJob',
    label: 'Run Job',
    description: 'Runs a container',
  });
});
