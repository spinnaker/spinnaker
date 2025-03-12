'use strict';

import { module } from 'angular';
import { from as observableFrom, Subject } from 'rxjs';
import { debounceTime, switchMap } from 'rxjs/operators';

export const DCOS_SERVERGROUP_CONFIGURE_WIZARD_CONTAINERSETTINGS_CONTROLLER =
  'spinnaker.dcos.serverGroup.configure.containerSettings';
export const name = DCOS_SERVERGROUP_CONFIGURE_WIZARD_CONTAINERSETTINGS_CONTROLLER; // for backwards compatibility
module(DCOS_SERVERGROUP_CONFIGURE_WIZARD_CONTAINERSETTINGS_CONTROLLER, []).controller(
  'dcosServerGroupContainerSettingsController',
  [
    '$scope',
    'dcosServerGroupConfigurationService',
    function ($scope, dcosServerGroupConfigurationService) {
      this.groupByRegistry = function (image) {
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
        return observableFrom(
          dcosServerGroupConfigurationService.configureCommand($scope.application, $scope.command, q),
        );
      }

      const imageSearchResultsStream = new Subject();

      imageSearchResultsStream.pipe(debounceTime(250), switchMap(searchImages)).subscribe();

      this.searchImages = function (q) {
        imageSearchResultsStream.next(q);
      };

      this.isParametersValid = function (parameters) {
        return !(typeof parameters === 'string' || parameters instanceof String);
      };

      this.addParameter = function () {
        if (!this.isParametersValid($scope.command.docker.parameters)) {
          $scope.command.docker.parameters = [];
        }

        $scope.command.docker.parameters.push({
          key: '',
          value: '',
        });
      };

      this.removeParameter = function (index) {
        $scope.command.docker.parameters.splice(index, 1);
      };
    },
  ],
);
