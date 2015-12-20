'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.gce.basicSettings', [
  require('angular-ui-router'),
  require('angular-ui-bootstrap'),
  require('../../../../core/serverGroup/configure/common/basicSettingsMixin.controller.js'),
  require('../../../../core/modal/wizard/modalWizard.service.js'),
  require('../../../../core/utils/rx.js'),
  require('../../../../core/image/image.reader.js'),
  require('../../../../core/naming/naming.service.js'),
  require('../../../gceRegionSelectField.directive.js'),
  require('../../../gceZoneSelectField.directive.js'),
  require('../../../gceNetworkSelectField.directive.js'),
])
  .controller('gceServerGroupBasicSettingsCtrl', function($scope, $controller, $uibModalStack, $state,
                                                          modalWizardService, rx, imageReader, namingService) {

    function searchImages(q) {
      $scope.command.backingData.filtered.images = [
        {
          message: '<span class="glyphicon glyphicon-spinning glyphicon-asterisk"></span> Finding results matching "' + q + '"...'
        }
      ];
      return rx.Observable.fromPromise(
        imageReader.findImages({
          provider: $scope.command.selectedProvider,
          q: q,
        })
      );
    }

    var imageSearchResultsStream = new rx.Subject();

    imageSearchResultsStream
      .throttle(250)
      .flatMapLatest(searchImages)
      .subscribe(function(data) {
        $scope.command.backingData.filtered.images = data.map(function(image) {
          if (image.message && !image.imageName) {
            return image;
          }
          return {
            account: image.account,
            imageName: image.imageName,
          };
        });
        $scope.command.backingData.packageImages = $scope.command.backingData.filtered.images;
     });

    this.searchImages = function(q) {
      imageSearchResultsStream.onNext(q);
    };

    this.enableAllImageSearch = () => {
      $scope.command.viewState.useAllImageSelection = true;
      this.searchImages('');
    };

    angular.extend(this, $controller('BasicSettingsMixin', {
      $scope: $scope,
      imageReader: imageReader,
      namingService: namingService,
      $uibModalStack: $uibModalStack,
      $state: $state,
    }));

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        modalWizardService.getWizard().markClean('location');
      } else {
        modalWizardService.getWizard().markDirty('location');
      }
    });

  });
