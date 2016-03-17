'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.pipeline.config.properties', [
    require('./../../stage/properties/create/persistedPropertyList.component.js'),
    require('./../../stage/properties/create/property.component.js')
  ]);
