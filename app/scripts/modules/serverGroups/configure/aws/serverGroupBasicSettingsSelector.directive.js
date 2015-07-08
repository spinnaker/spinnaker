'use strict';

angular.module('spinnaker.serverGroup.configure.aws')
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
  .controller('ServerGroupBasicSettingsSelectorCtrl', function($scope, RxService, imageService, namingService, $modalStack, $state) {
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
          if (image.message && !image.imageName) {
            return image;
          }
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
      $scope.latestServerGroup = this.getLatestServerGroup();
      return !_.find($scope.application.clusters, { name: name });
    };

    this.getNamePreview = function() {
      var command = $scope.command;
      if (!command) {
        return '';
      }
      return namingService.getClusterName($scope.application.name, command.stack, command.freeFormDetails);
    };

    this.getLatestServerGroup = function() {
      var command = $scope.command;
      var cluster = namingService.getClusterName($scope.application.name, command.stack, command.freeFormDetails);
      var inCluster = $scope.application.serverGroups.filter(function(serverGroup) {
        return serverGroup.cluster === cluster &&
          serverGroup.account === command.credentials &&
          serverGroup.region === command.region;
      }).sort(function (a, b) { return a.createdTime - b.createdTime; });
      return inCluster.length ? inCluster.pop() : null;
    };

    this.getLatestServerGroup();

    this.showPreviewAsWarning = function() {
      var mode = $scope.command.viewState.mode,
          createsNewCluster = this.createsNewCluster();

      return (mode === 'create' && !createsNewCluster) || (mode !== 'create' && createsNewCluster);
    };

    this.navigateToLatestServerGroup = function() {
      var latest = $scope.latestServerGroup,
          params = {
            provider: 'aws',
            accountId: latest.account,
            region: latest.region,
            serverGroup: latest.name
          };

      $modalStack.dismissAll();
      if ($state.is('home.applications.application.insight.clusters')) {
        $state.go('.serverGroup', params);
      } else {
        $state.go('^.serverGroup', params);
      }
    };

  });
