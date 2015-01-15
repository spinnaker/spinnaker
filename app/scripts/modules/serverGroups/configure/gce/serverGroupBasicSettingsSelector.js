'use strict';

angular.module('deckApp.serverGroup.configure.gce')
  .directive('gceServerGroupBasicSettingsSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
        application: '=',
        hideClusterNamePreview: '=',
      },
      templateUrl: 'scripts/modules/serverGroups/configure/gce/serverGroupBasicSettingsDirective.html',
      controller: 'gceServerGroupBasicSettingsSelectorCtrl as basicSettingsCtrl',
    };
  })
  .controller('gceServerGroupBasicSettingsSelectorCtrl', function($scope, RxService, imageService, deploymentStrategiesService, serverGroupService) {
    function searchImages(q) {
      $scope.allImageSearchResults = [
        {
          message: '<span class="glyphicon glyphicon-spinning glyphicon-asterisk"></span> Finding results matching "' + q + '"...'
        }
      ];
      return new RxService.Observable.fromPromise(
        imageService.findImages({
          provider: 'gce',
          q: q,
          region: $scope.command.region
        })
      );
    }

    var imageSearchResultsStream = new RxService.Subject();

    imageSearchResultsStream
      .throttle(250)
      .flatMapLatest(searchImages)
      .subscribe(function (data) {
        $scope.allImageSearchResults = data.map(function(image) {
          return {
            imageName: image.imageName,
            ami: image.amis ? image.amis[$scope.command.region][0] : null
          };
        });
      });

    this.searchImages = function(q) {
      imageSearchResultsStream.onNext(q);
    };

    this.createsNewCluster = function() {
      var name = this.getNamePreview();
      return !_.find($scope.application.clusters, { name: name });
    };

    this.getNamePreview = function() {
      var command = $scope.command;
      if (!command) {
        return '';
      }
      return serverGroupService.getClusterName($scope.application.name, command.stack, command.freeFormDetails);
    };

    // Use undefined to check for the presence of the 'strategy' field, which is added to the command
    // on "clone" operations, but not "create new" operations, where it doesn't seem valid to have a strategy
    // (assuming "create new" is used to create a brand new cluster).
    //
    // The field is hidden on the form if no deployment strategies are present on the scope.
    if ($scope.command.strategy !== undefined) {
      deploymentStrategiesService.listAvailableStrategies().then(function (strategies) {
        $scope.deploymentStrategies = strategies;
      });
    }
  });
