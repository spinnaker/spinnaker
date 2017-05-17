'use strict';

const angular = require('angular');
import { Observable, Subject } from 'rxjs';

import { NAMING_SERVICE, V2_MODAL_WIZARD_SERVICE } from '@spinnaker/core';

module.exports = angular.module('spinnaker.serverGroup.configure.kubernetes.basicSettings', [
  require('angular-ui-router').default,
  require('angular-ui-bootstrap'),
  V2_MODAL_WIZARD_SERVICE,
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
