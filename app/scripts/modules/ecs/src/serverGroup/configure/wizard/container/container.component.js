'use strict';

import { module } from 'angular';
import { Observable, Subject } from 'rxjs';

export const ECS_SERVERGROUP_CONFIGURE_WIZARD_CONTAINER_CONTAINER_COMPONENT =
  'spinnaker.ecs.serverGroup.configure.wizard.container.component';
export const name = ECS_SERVERGROUP_CONFIGURE_WIZARD_CONTAINER_CONTAINER_COMPONENT; // for backwards compatibility
module(ECS_SERVERGROUP_CONFIGURE_WIZARD_CONTAINER_CONTAINER_COMPONENT, [])
  .component('ecsServerGroupContainer', {
    bindings: {
      command: '=',
      application: '=',
    },
    templateUrl: require('./container.component.html'),
  })
  .controller('ecsContainerImageController', [
    '$scope',
    'ecsServerGroupConfigurationService',
    function($scope, ecsServerGroupConfigurationService) {
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

      function searchImages(cmd, q) {
        return Observable.fromPromise(ecsServerGroupConfigurationService.configureCommand(cmd, q));
      }

      const imageSearchResultsStream = new Subject();

      imageSearchResultsStream
        .debounceTime(250)
        .switchMap(searchImages)
        .subscribe();

      this.searchImages = function(q) {
        imageSearchResultsStream.next(q);
      };
    },
  ]);
