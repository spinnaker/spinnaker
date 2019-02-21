'use strict';

const angular = require('angular');
import { Observable, Subject } from 'rxjs';

import { IMAGE_READER, ModalWizard } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.serverGroup.configure.openstack.instanceSettings', [
    require('@uirouter/angularjs').default,
    require('angular-ui-bootstrap'),
    IMAGE_READER,
    require('../../../../instance/osInstanceTypeSelectField.directive').name,
  ])
  .controller('openstackServerGroupInstanceSettingsCtrl', [
    '$scope',
    '$controller',
    '$uibModalStack',
    '$state',
    'imageReader',
    function($scope, $controller, $uibModalStack, $state, imageReader) {
      function ensureCommandBackingDataFilteredExists() {
        if (!$scope.command.backingData) {
          $scope.command.backingData = { filtered: {} };
        } else if (!$scope.command.backingData.filtered) {
          $scope.command.backingData.filtered = {};
        }
      }

      function searchImages(q) {
        ensureCommandBackingDataFilteredExists();
        $scope.command.backingData.filtered.images = [
          {
            message: `<loading-spinner size="'nano'"></loading-spinner> Finding results matching "${q}"...`,
          },
        ];
        return Observable.fromPromise(
          imageReader.findImages({
            provider: $scope.command.selectedProvider,
            q: q,
            region: $scope.command.region,
            account: $scope.command.credentials,
          }),
        );
      }

      var imageSearchResultsStream = new Subject();

      imageSearchResultsStream
        .debounceTime(250)
        .switchMap(searchImages)
        .subscribe(function(data) {
          ensureCommandBackingDataFilteredExists();
          $scope.command.backingData.filtered.images = data;
          $scope.command.backingData.packageImages = $scope.command.backingData.filtered.images;
        });

      this.searchImages = function(q) {
        imageSearchResultsStream.next(q);
      };

      $scope.$watch('instanceSettings.$valid', function(newVal) {
        if (newVal) {
          ModalWizard.markClean('instance-settings');
          ModalWizard.markComplete('instance-settings');
        } else {
          ModalWizard.markIncomplete('instance-settings');
        }
      });
    },
  ]);
