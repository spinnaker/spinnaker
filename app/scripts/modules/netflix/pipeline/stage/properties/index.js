'use strict';

let angular = require('angular');

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {NAMING_SERVICE} from 'core/naming/naming.service';

require('./propertyStage.less');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.property', [
  require('./create/propertyStage'),
  require('./create/persistedPropertyList.component'),
  require('./create/property.component'),
  require('./create/propertyExecutionDetails.controller.js'),
  require('core/deploymentStrategy/deploymentStrategy.module'),
  ACCOUNT_SERVICE,
  NAMING_SERVICE,
  require('../../../fastProperties/wizard/propertyDetails/fastPropertyConstraintsSelector.directive'),
]);
