'use strict';

angular.module('spinnaker.serverGroup.configure.gce')
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
  .controller('gceServerGroupBasicSettingsSelectorCtrl', function($scope, RxService, imageService, namingService) {
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
      return namingService.getClusterName($scope.application.name, command.stack, command.freeFormDetails);
    };

  });
