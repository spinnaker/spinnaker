'use strict';

let angular = require('angular');

import {V2_MODAL_WIZARD_SERVICE} from 'core/modal/wizard/v2modalWizard.service';
import {IMAGE_READER} from 'core/image/image.reader';
import {NAMING_SERVICE} from 'core/naming/naming.service';

module.exports = angular.module('spinnaker.oraclebmcs.serverGroup.configure.wizard.basicSettings.controller', [
  require('angular-ui-router').default,
  require('angular-ui-bootstrap'),
  V2_MODAL_WIZARD_SERVICE,
  IMAGE_READER,
  NAMING_SERVICE,
])
  .controller('oraclebmcsServerGroupBasicSettingsCtrl', function ($scope,
                                                                  v2modalWizardService,
                                                                  $state,
                                                                  $uibModalStack,
                                                                  $controller,
                                                                  imageReader,
                                                                  namingService) {

    angular.extend(this, $controller('BasicSettingsMixin', {
      $scope: $scope,
      imageReader: imageReader,
      namingService: namingService,
      $uibModalStack: $uibModalStack,
      $state: $state,
    }));
  });
