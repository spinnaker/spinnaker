'use strict';

const angular = require('angular');
import { Observable, Subject } from 'rxjs';

import { IMAGE_READER, ExpectedArtifactSelectorViewController, NgGCEImageArtifactDelegate } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.google.serverGroup.configure.wizard.basicSettings.controller', [
    require('@uirouter/angularjs').default,
    require('angular-ui-bootstrap'),
    IMAGE_READER,
    require('../../../../gceRegionSelectField.directive').name,
    require('../../../../gceNetworkSelectField.directive').name,
    require('../../../../subnet/subnetSelectField.directive').name,
  ])
  .controller('gceServerGroupBasicSettingsCtrl', [
    '$scope',
    '$controller',
    '$uibModalStack',
    '$state',
    'imageReader',
    function($scope, $controller, $uibModalStack, $state, imageReader) {
      function fetchImagesForAccount() {
        return Observable.fromPromise(
          imageReader.findImages({
            account: $scope.command.credentials,
            provider: $scope.command.selectedProvider,
            q: '*',
          }),
        );
      }

      const imageSearchResultsStream = new Subject();
      imageSearchResultsStream.switchMap(fetchImagesForAccount).subscribe(images => {
        $scope.command.backingData.allImages = images;
        $scope.command.backingData.packageImages = images;
      });

      this.accountUpdated = () => {
        imageSearchResultsStream.next();
      };

      this.enableAllImageSearch = () => {
        $scope.command.viewState.useAllImageSelection = true;
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
          const pattern = $scope.command.viewState.templatingEnabled ? /^([a-zA-Z0-9]*(\${.+})*)*$/ : /^[a-zA-Z0-9]*$/;
          return pattern.test(stack);
        },
      };

      this.detailPattern = {
        test: function(detail) {
          const pattern = $scope.command.viewState.templatingEnabled
            ? /^([a-zA-Z0-9-]*(\${.+})*)*$/
            : /^[a-zA-Z0-9-]*$/;
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
    },
  ]);
