'use strict';

angular.module('deckApp.serverGroup.configure.aws')
  .directive('awsServerGroupBasicSettingsSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
        application: '=',
        hideClusterNamePreview: '=',
      },
      templateUrl: 'scripts/modules/serverGroups/configure/aws/serverGroupBasicSettingsDirective.html',
      controller: 'ServerGroupBasicSettingsSelectorCtrl as basicSettingsCtrl',
    };
  })
  .controller('ServerGroupBasicSettingsSelectorCtrl', function($scope, RxService, imageService, serverGroupService) {
    function searchImages(q) {
      $scope.allImageSearchResults = [
        {
          message: '<span class="glyphicon glyphicon-spinning glyphicon-asterisk"></span> Finding results matching "' + q + '"...'
        }
      ];
      return new RxService.Observable.fromPromise(
        imageService.findImages({
          provider: $scope.command.selectedProvider,
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
  });
