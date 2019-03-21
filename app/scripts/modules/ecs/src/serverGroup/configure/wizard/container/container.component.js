'use strict';

const angular = require('angular');
import { Observable, Subject } from 'rxjs';

module.exports = angular
  .module('spinnaker.ecs.serverGroup.configure.wizard.container.component', [])
  .component('ecsServerGroupContainer', {
    bindings: {
      command: '=',
      application: '=',
    },
    templateUrl: require('./container.component.html'),
  })
  .controller('ecsContainerImageController', function($scope, ecsServerGroupConfigurationService) {
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

    var imageSearchResultsStream = new Subject();

    imageSearchResultsStream
      .debounceTime(250)
      .switchMap(searchImages)
      .subscribe();

    this.searchImages = function(q) {
      imageSearchResultsStream.next(q);
    };
  });
