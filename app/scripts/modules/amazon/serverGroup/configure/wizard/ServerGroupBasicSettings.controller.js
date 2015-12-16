'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.aws.basicSettings', [
  require('angular-ui-router'),
  require('angular-ui-bootstrap'),
  require('../../../../core/serverGroup/configure/common/basicSettingsMixin.controller.js'),
  require('../../../../core/modal/wizard/modalWizard.service.js'),
  require('../../../../core/utils/rx.js'),
  require('../../../../core/image/image.reader.js'),
  require('../../../../core/naming/naming.service.js'),
])
  .controller('awsServerGroupBasicSettingsCtrl', function($scope, $controller, $uibModalStack, $state,
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
          region: $scope.command.region
        })
      );
    }

    var imageSearchResultsStream = new rx.Subject();

    imageSearchResultsStream
      .throttle(250)
      .flatMapLatest(searchImages)
      .subscribe(function (data) {
        $scope.command.backingData.filtered.images = data.map(function(image) {
          if (image.message && !image.imageName) {
            return image;
          }
          return {
            imageName: image.imageName,
            ami: image.amis && image.amis[$scope.command.region] ? image.amis[$scope.command.region][0] : null,
            virtualizationType: image.attributes ? image.attributes.virtualizationType : null,
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

    this.imageChanged = (image) => {
      $scope.command.virtualizationType = image.virtualizationType;
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
