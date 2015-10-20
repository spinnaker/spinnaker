'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.serverGroup.basicSettings.controller', [
    require('angular-ui-bootstrap'),
    require('angular-ui-router'),
    require('../../../utils/rx.js'),
    require('../../../utils/lodash.js'),
    require('../../../naming/naming.service.js'),
    require('../../../image/image.reader.js')
  ])
  .controller('BasicSettingsMixin', function ($scope, RxService, imageReader, namingService, $uibModalStack, $state, _) {
    function searchImages(q) {
      $scope.allImageSearchResults = [
        {
          message: '<span class="glyphicon glyphicon-spinning glyphicon-asterisk"></span> Finding results matching "' + q + '"...'
        }
      ];
      return RxService.Observable.fromPromise(
        imageReader.findImages({
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
            ami: image.amis && image.amis[$scope.command.region] ? image.amis[$scope.command.region][0] : null
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

    this.showPreviewAsWarning = function() {
      var mode = $scope.command.viewState.mode,
        createsNewCluster = this.createsNewCluster();

      return (mode === 'create' && !createsNewCluster) || (mode !== 'create' && createsNewCluster);
    };

    this.navigateToLatestServerGroup = function() {
      var latest = $scope.latestServerGroup,
        params = {
          provider: $scope.command.selectedProvider,
          accountId: latest.account,
          region: latest.region,
          serverGroup: latest.name
        };

      $uibModalStack.dismissAll();
      if ($state.is('home.applications.application.insight.clusters')) {
        $state.go('.serverGroup', params);
      } else {
        $state.go('^.serverGroup', params);
      }
    };

    this.stackPattern = {
      test: function(stack) {
        var pattern = $scope.command.viewState.templatingEnabled ?
          /^([a-zA-z_0-9._]*(\${.+})*)*$/ :
          /^[a-zA-z_0-9._]*$/;
        return pattern.test(stack);
      }
    };

    this.detailPattern = {
      test: function(stack) {
        var pattern = $scope.command.viewState.templatingEnabled ?
          /^([a-zA-z_0-9._-]*(\${.+})*)*$/ :
          /^[a-zA-z_0-9._-]*$/;
        return pattern.test(stack);
      }
    };
  })
.name;
