'use strict';

import {Observable, Subject} from 'rxjs';
import {V2_MODAL_WIZARD_SERVICE} from 'core/modal/wizard/v2modalWizard.service';
import {NAMING_SERVICE} from 'core/naming/naming.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.kubernetes.basicSettings', [
  require('angular-ui-router'),
  require('angular-ui-bootstrap'),
  require('core/serverGroup/configure/common/basicSettingsMixin.controller.js'),
  V2_MODAL_WIZARD_SERVICE,
  require('core/image/image.reader.js'),
  NAMING_SERVICE,
])
  .controller('kubernetesServerGroupBasicSettingsController', function($scope, $controller, $uibModalStack, $state,
                                                                       v2modalWizardService, kubernetesImageReader, namingService,
                                                                       kubernetesServerGroupConfigurationService) {

    function searchImages(q) {
      $scope.command.backingData.filtered.images = [
        {
          message: '<span class="glyphicon glyphicon-spinning glyphicon-asterisk"></span> Finding results matching "' + q + '"...'
        }
      ];
      return Observable.fromPromise(
        kubernetesServerGroupConfigurationService
          .configureCommand($scope.application, $scope.command, q)
      );
    }

    var imageSearchResultsStream = new Subject();

    imageSearchResultsStream
      .debounceTime(250)
      .switchMap(searchImages)
      .subscribe();

    this.searchImages = function(q) {
      imageSearchResultsStream.next(q);
    };

    angular.extend(this, $controller('BasicSettingsMixin', {
      $scope: $scope,
      imageReader: kubernetesImageReader,
      namingService: namingService,
      $uibModalStack: $uibModalStack,
      $state: $state,
    }));
  });
