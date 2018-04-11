'use strict';

const angular = require('angular');

import { IMAGE_READER, V2_MODAL_WIZARD_SERVICE } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.oraclebmcs.serverGroup.configure.wizard.basicSettings.controller', [
    require('@uirouter/angularjs').default,
    require('angular-ui-bootstrap'),
    V2_MODAL_WIZARD_SERVICE,
    IMAGE_READER,
  ])
  .controller('oraclebmcsServerGroupBasicSettingsCtrl', function(
    $scope,
    v2modalWizardService,
    $state,
    $uibModalStack,
    $controller,
    imageReader,
  ) {
    angular.extend(
      this,
      $controller('BasicSettingsMixin', {
        $scope: $scope,
        imageReader: imageReader,
        $uibModalStack: $uibModalStack,
        $state: $state,
      }),
    );
  });
