'use strict';

require('./whatsNew.less');

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.whatsNew.directive', [
    require('angular-marked'),
    require('../../core/cache/viewStateCache.js'),
    require('./whatsNew.read.service.js'),
    require('../../core/utils/timeFormatters.js'),
  ])
  .config(function (markedProvider) {
    markedProvider.setOptions(
      {gfm: true}
    );
  })
  .directive('whatsNew', function (whatsNewReader, viewStateCache) {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: require('./whatsNew.directive.html'),
      controller: function($scope, $uibModal) {

        // single cache, so we will use the cache name as the key, also
        var cacheId = 'whatsNew';

        var whatsNewViewStateCache = viewStateCache[cacheId] || viewStateCache.createCache(cacheId, { version: 1 });

        $scope.viewState = whatsNewViewStateCache.get(cacheId) || {
          updateLastViewed: null,
        };

        whatsNewReader.getWhatsNewContents().then(function(result) {
          if (result) {
            $scope.fileContents = result.contents;
            $scope.fileLastUpdated = result.lastUpdated;
            $scope.lastUpdatedDate = new Date(result.lastUpdated);
          }
        });

        $scope.showWhatsNew = function() {
          $scope.viewState.updateLastViewed = $scope.fileLastUpdated;
          whatsNewViewStateCache.put(cacheId, $scope.viewState);
          $uibModal.open({
            templateUrl: require('./whatsNew.directive.modal.html'),
            scope: $scope,
          });
        };

        $scope.updatesUnread = function() {
          return $scope.fileLastUpdated && $scope.fileLastUpdated !== $scope.viewState.updateLastViewed;
        };

      }
    };
  });
