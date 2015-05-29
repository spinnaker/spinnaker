'use strict';

angular
  .module('spinnaker.fastproperties', [
    'spinnaker.fastProperties.controller',
    'spinnaker.applicationProperties.controller',
    'spinnaker.fastPropertyScope.selection.directive',
    'spinnaker.deleteFastProperty.controller',
    'spinnaker.fastProperties.rollouts.controller',
    'spinnaker.fastProperties.data.controller',
    'spinnaker.fastProperty.progressBar.directive',
    'spinnaker.fastProperty.constraints.directive',
    'spinnaker.fastProperty.transformer.service',
  ]);
