'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.kubernetes.basicSettings', [
  require('angular-ui-router'),
  require('angular-ui-bootstrap'),
  require('../../../../core/serverGroup/configure/common/basicSettingsMixin.controller.js'),
  require('../../../../core/modal/wizard/v2modalWizard.service.js'),
  require('../../../../core/utils/rx.js'),
  require('../../../../core/image/image.reader.js'),
  require('../../../../core/naming/naming.service.js'),
])
  .controller('kubernetesServerGroupBasicSettingsController', function($scope, $controller, $uibModalStack, $state,
                                                                       v2modalWizardService, rx, kubernetesImageReader, namingService,
                                                                       kubernetesServerGroupConfigurationService) {

    function searchImages(q) {
      $scope.command.backingData.filtered.images = [
        {
          message: '<span class="glyphicon glyphicon-spinning glyphicon-asterisk"></span> Finding results matching "' + q + '"...'
        }
      ];
      return rx.Observable.fromPromise(
        kubernetesServerGroupConfigurationService
          .configureCommand($scope.application, $scope.command, q)
      );
    }

    var imageSearchResultsStream = new rx.Subject();

    imageSearchResultsStream
      .throttle(250)
      .flatMapLatest(searchImages)
      .subscribe();

    this.searchImages = function(q) {
      imageSearchResultsStream.onNext(q);
    };

    angular.extend(this, $controller('BasicSettingsMixin', {
      $scope: $scope,
      imageReader: kubernetesImageReader,
      namingService: namingService,
      $uibModalStack: $uibModalStack,
      $state: $state,
    }));

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        v2modalWizardService.markClean('location');
        v2modalWizardService.markComplete('location');
      } else {
        v2modalWizardService.markIncomplete('location');
      }
    });
  });
