'use strict';

const angular = require('angular');
import { Observable, Subject } from 'rxjs';

module.exports = angular
  .module('spinnaker.dcos.serverGroup.configure.containerSettings', [])
  .controller('dcosServerGroupContainerSettingsController', [
    '$scope',
    'dcosServerGroupConfigurationService',
    function($scope, dcosServerGroupConfigurationService) {
      this.groupByRegistry = function(image) {
        if (image) {
          if (image.fromContext) {
            return 'Find Image Result(s)';
          } else if (image.fromTrigger) {
            return 'Images from Trigger(s)';
          } else {
            return image.registry;
          }
        }
      };

      function searchImages(q) {
        return Observable.fromPromise(
          dcosServerGroupConfigurationService.configureCommand($scope.application, $scope.command, q),
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

      this.isParametersValid = function(parameters) {
        return !(typeof parameters === 'string' || parameters instanceof String);
      };

      this.addParameter = function() {
        if (!this.isParametersValid($scope.command.docker.parameters)) {
          $scope.command.docker.parameters = [];
        }

        $scope.command.docker.parameters.push({
          key: '',
          value: '',
        });
      };

      this.removeParameter = function(index) {
        $scope.command.docker.parameters.splice(index, 1);
      };
    },
  ]);
