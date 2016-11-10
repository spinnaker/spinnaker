'use strict';

let angular = require('angular');
import {ACCOUNT_SERVICE} from 'core/account/account.service';

require('./propertyStage.less');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.property', [
  require('./create/propertyStage'),
  require('./create/persistedPropertyList.component'),
  require('./create/property.component'),
  require('./create/propertyExecutionDetails.controller.js'),
  require('core/deploymentStrategy/deploymentStrategy.module.js'),
  require('core/serverGroup/serverGroup.read.service.js'),
  ACCOUNT_SERVICE,
  require('core/naming/naming.service.js'),
  require('../../../fastProperties/modal/fastPropertyConstraint.directive'),
  require('../../../fastProperties/modal/wizard/fastPropertyConstraintsSelector.directive'),
]);
