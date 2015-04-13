'use strict';

angular
  .module('deckApp.whatsNew.directive', [
    'ui.bootstrap',
    'hc.marked',
    'deckApp.caches.viewStateCache',
    'deckApp.whatsNew.read.service',
    'deckApp.utils.timeFormatters',
  ])
  .config(function (markedProvider) {
    markedProvider.setOptions(
      {gfm: true}
    );
  })
  .directive('whatsNew', function (whatsNewReader, viewStateCache) {
    return {
      restrict: 'E',
      templateUrl: 'scripts/modules/whatsNew/whatsNew.directive.html',
      controller: function($scope, $modal) {

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
          $modal.open({
            templateUrl: 'scripts/modules/whatsNew/whatsNew.directive.modal.html',
            scope: $scope,
          });
        };

        $scope.updatesUnread = function() {
          return $scope.fileLastUpdated && $scope.fileLastUpdated !== $scope.viewState.updateLastViewed;
        };

      }
    };
  });
