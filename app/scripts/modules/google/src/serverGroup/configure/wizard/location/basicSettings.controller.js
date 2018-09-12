'use strict';

const angular = require('angular');
import { Observable, Subject } from 'rxjs';

import { IMAGE_READER, ExpectedArtifactSelectorViewController, NgGCEImageArtifactDelegate } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.google.serverGroup.configure.wizard.basicSettings.controller', [
    require('@uirouter/angularjs').default,
    require('angular-ui-bootstrap'),
    IMAGE_READER,
    require('../../../../gceRegionSelectField.directive.js').name,
    require('../../../../gceNetworkSelectField.directive.js').name,
    require('../../../../subnet/subnetSelectField.directive.js').name,
  ])
  .controller('gceServerGroupBasicSettingsCtrl', function($scope, $controller, $uibModalStack, $state, imageReader) {
    function searchImages(q) {
      $scope.command.backingData.filtered.images = [
        {
          message: `<loading-spinner size="'nano'"></loading-spinner> Finding results matching "${q}"...`,
        },
      ];
      return Observable.fromPromise(
        imageReader.findImages({
          provider: $scope.command.selectedProvider,
          q: q,
        }),
      );
    }

    var imageSearchResultsStream = new Subject();

    imageSearchResultsStream
      .debounceTime(250)
      .switchMap(searchImages)
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
      imageSearchResultsStream.next(q);
    };

    this.enableAllImageSearch = () => {
      $scope.command.viewState.useAllImageSelection = true;
      this.searchImages('');
    };

    angular.extend(
      this,
      $controller('BasicSettingsMixin', {
        $scope: $scope,
        imageReader: imageReader,
        $uibModalStack: $uibModalStack,
        $state: $state,
      }),
    );

    this.stackPattern = {
      test: function(stack) {
        var pattern = $scope.command.viewState.templatingEnabled ? /^([a-zA-Z0-9]*(\${.+})*)*$/ : /^[a-zA-Z0-9]*$/;
        return pattern.test(stack);
      },
    };

    this.detailPattern = {
      test: function(detail) {
        var pattern = $scope.command.viewState.templatingEnabled ? /^([a-zA-Z0-9-]*(\${.+})*)*$/ : /^[a-zA-Z0-9-]*$/;
        return pattern.test(detail);
      },
    };

    this.getSubnetPlaceholder = () => {
      if (!$scope.command.region) {
        return '(Select an account)';
      } else if ($scope.command.viewState.autoCreateSubnets) {
        return '(Subnet will be automatically selected)';
      } else if ($scope.command.viewState.autoCreateSubnets === null) {
        return '(Subnets not supported)';
      } else {
        return null;
      }
    };

    this.imageSources = ['artifact', 'priorStage'];

    const gceImageDelegate = new NgGCEImageArtifactDelegate($scope);
    $scope.gceImageArtifact = {
      showCreateArtifactForm: false,
      delegate: gceImageDelegate,
      controller: new ExpectedArtifactSelectorViewController(gceImageDelegate),
    };
  });
