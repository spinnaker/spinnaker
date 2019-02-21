'use strict';

import { ManualExecutionBake } from './ManualExecutionBake';
import { Registry } from 'core/registry';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.stage.bakeStage', [require('./bakeStage.transformer').name])
  .config(function() {
    Registry.pipeline.registerStage({
      useBaseProvider: true,
      label: 'Bake',
      description: 'Bakes an image',
      key: 'bake',
      restartable: true,
      manualExecutionComponent: ManualExecutionBake,
    });
  })
  .run(['bakeStageTransformer', function(bakeStageTransformer) {
    Registry.pipeline.registerTransformer(bakeStageTransformer);
  }]);
