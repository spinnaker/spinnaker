'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import * as angular from 'angular';
import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap';
import { from as observableFrom, Subject } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import {
  ArtifactTypePatterns,
  excludeAllTypesExcept,
  ExpectedArtifactSelectorViewController,
  IMAGE_READER,
  NgGCEImageArtifactDelegate,
} from '@spinnaker/core';

import { GOOGLE_GCENETWORKSELECTFIELD_DIRECTIVE } from '../../../../gceNetworkSelectField.directive';
import { GOOGLE_GCEREGIONSELECTFIELD_DIRECTIVE } from '../../../../gceRegionSelectField.directive';
import { GceImageReader } from '../../../../image';
import { GOOGLE_SUBNET_SUBNETSELECTFIELD_DIRECTIVE } from '../../../../subnet/subnetSelectField.directive';

export const GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_LOCATION_BASICSETTINGS_CONTROLLER =
  'spinnaker.google.serverGroup.configure.wizard.basicSettings.controller';
export const name = GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_LOCATION_BASICSETTINGS_CONTROLLER; // for backwards compatibility
angular
  .module(GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_LOCATION_BASICSETTINGS_CONTROLLER, [
    UIROUTER_ANGULARJS,
    ANGULAR_UI_BOOTSTRAP,
    IMAGE_READER,
    GOOGLE_GCEREGIONSELECTFIELD_DIRECTIVE,
    GOOGLE_GCENETWORKSELECTFIELD_DIRECTIVE,
    GOOGLE_SUBNET_SUBNETSELECTFIELD_DIRECTIVE,
  ])
  .controller('gceServerGroupBasicSettingsCtrl', [
    '$scope',
    '$controller',
    '$uibModalStack',
    '$state',
    function ($scope, $controller, $uibModalStack, $state) {
      function fetchImagesForAccount() {
        return observableFrom(
          GceImageReader.findImages({
            account: $scope.command.credentials,
            provider: $scope.command.selectedProvider,
            q: '*',
          }),
        );
      }

      const imageSearchResultsStream = new Subject();
      imageSearchResultsStream.pipe(switchMap(fetchImagesForAccount)).subscribe((images) => {
        $scope.command.backingData.allImages = images;
      });

      this.accountUpdated = () => {
        imageSearchResultsStream.next();
      };

      this.selectImage = (image) => {
        // called from a React component
        $scope.$apply(() => {
          $scope.command.image = image;
        });
      };

      angular.extend(
        this,
        $controller('BasicSettingsMixin', {
          $scope: $scope,
          imageReader: GceImageReader,
          $uibModalStack: $uibModalStack,
          $state: $state,
        }),
      );

      this.stackPattern = {
        test: function (stack) {
          const pattern = $scope.command.viewState.templatingEnabled ? /^([a-zA-Z0-9]*(\${.+})*)*$/ : /^[a-zA-Z0-9]*$/;
          return pattern.test(stack);
        },
      };

      this.detailPattern = {
        test: function (detail) {
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

      this.excludedImageArtifactTypes = excludeAllTypesExcept(ArtifactTypePatterns.CUSTOM_OBJECT);

      this.onImageArtifactEdited = (artifact) => {
        $scope.$applyAsync(() => {
          $scope.command.imageArtifactId = null;
          $scope.command.imageArtifact = artifact;
        });
      };

      this.onImageArtifactSelected = (expectedArtifact) => {
        this.onChangeImageArtifactId(expectedArtifact.id);
      };

      this.onChangeImageArtifactId = (artifactId) => {
        $scope.$applyAsync(() => {
          $scope.command.imageArtifactId = artifactId;
          $scope.command.imageArtifact = null;
        });
      };

      this.onImageArtifactAccountSelected = (accountName) => {
        $scope.$applyAsync(() => {
          $scope.command.imageAccountName = accountName;
        });
      };

      const gceImageDelegate = new NgGCEImageArtifactDelegate($scope);
      $scope.gceImageArtifact = {
        showCreateArtifactForm: false,
        delegate: gceImageDelegate,
        controller: new ExpectedArtifactSelectorViewController(gceImageDelegate),
      };
    },
  ]);
