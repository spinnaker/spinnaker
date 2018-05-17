'use strict';

import { Registry } from 'core/registry';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.findImageFromTagsStage', []).config(function() {
  Registry.pipeline.registerStage({
    useBaseProvider: true,
    key: 'findImageFromTags',
    label: 'Find Image from Tags',
    description: 'Finds an image to deploy from existing tags',
  });
});
