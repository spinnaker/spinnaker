'use strict';

import { Registry } from 'core/registry';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.tagImageStage', []).config(function() {
  Registry.pipeline.registerStage({
    useBaseProvider: true,
    key: 'upsertImageTags',
    label: 'Tag Image',
    description: 'Tags an image',
  });
});
