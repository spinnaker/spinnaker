'use strict';

const angular = require('angular');
import { Observable, Subject } from 'rxjs';

import { IMAGE_READER, NAMING_SERVICE, V2_MODAL_WIZARD_SERVICE } from '@spinnaker/core';
import { SUBNET_SELECT_FIELD_COMPONENT } from 'amazon/subnet/subnetSelectField.component';

module.exports = angular.module('spinnaker.amazon.serverGroup.configure.basicSettings', [
  require('@uirouter/angularjs').default,
  require('angular-ui-bootstrap'),
  V2_MODAL_WIZARD_SERVICE,
  IMAGE_READER,
  NAMING_SERVICE,
  SUBNET_SELECT_FIELD_COMPONENT,
])
  .controller('awsServerGroupBasicSettingsCtrl', function($scope, $controller, $uibModalStack, $state,
                                                          v2modalWizardService, imageReader, namingService) {

    function searchImages(q) {
      $scope.command.backingData.filtered.images = [
        {
          message: '<span class="fa fa-cog fa-spin"></span> Finding results matching "' + q + '"...'
        }
      ];
      return Observable.fromPromise(
        imageReader.findImages({
          provider: $scope.command.selectedProvider,
          q: q,
          region: $scope.command.region
        })
      ).map(function (result) {
        if (result.length === 0 && q.startsWith('ami-') && q.length === 12) {
          // allow 'advanced' users to continue with just an ami id (backing image may not have been indexed yet)
          let record = {
            imageName: q,
            amis: {},
            attributes: {
              virtualizationType: '*',
            }
          };

          // trust that the specific image exists in the selected region
          record.amis[$scope.command.region] = [q];
          result = [record];
        }

        return result;
      });
    }

    var imageSearchResultsStream = new Subject();

    imageSearchResultsStream
      .debounceTime(250)
      .switchMap(searchImages)
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
      imageSearchResultsStream.next(q);
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
        v2modalWizardService.markClean('location');
        v2modalWizardService.markComplete('location');
      } else {
        v2modalWizardService.markIncomplete('location');
      }
    });

  });
