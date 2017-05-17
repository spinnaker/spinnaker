'use strict';

const angular = require('angular');

import { ACCOUNT_SERVICE, NAMING_SERVICE } from '@spinnaker/core';

import './propertyStage.less';

module.exports = angular.module('spinnaker.netflix.pipeline.stage.property', [
  require('./create/propertyStage'),
  require('./create/persistedPropertyList.component'),
  require('./create/property.component'),
  require('./create/propertyExecutionDetails.controller.js'),
  ACCOUNT_SERVICE,
  NAMING_SERVICE,
  require('../../../fastProperties/wizard/propertyDetails/fastPropertyConstraintsSelector.directive'),
]);
