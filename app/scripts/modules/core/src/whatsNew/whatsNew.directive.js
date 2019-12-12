'use strict';

import { module } from 'angular';

import { HtmlRenderer, Parser } from 'commonmark';

import { ViewStateCache } from 'core/cache';
import { TIME_FORMATTERS } from 'core/utils/timeFormatters';
import { WhatsNewReader } from './WhatsNewReader';

import './whatsNew.less';

export const CORE_WHATSNEW_WHATSNEW_DIRECTIVE = 'spinnaker.core.whatsNew.directive';
export const name = CORE_WHATSNEW_WHATSNEW_DIRECTIVE; // for backwards compatibility
module(CORE_WHATSNEW_WHATSNEW_DIRECTIVE, [TIME_FORMATTERS]).directive('whatsNew', function() {
  return {
    restrict: 'E',
    replace: true,
    templateUrl: require('./whatsNew.directive.html'),
    controller: [
      '$scope',
      '$uibModal',
      function($scope, $uibModal) {
        const parser = new Parser();
        const renderer = new HtmlRenderer();

        // single cache, so we will use the cache name as the key, also
        const cacheId = 'whatsNew';

        const whatsNewViewStateCache = ViewStateCache[cacheId] || ViewStateCache.createCache(cacheId, { version: 1 });

        $scope.viewState = whatsNewViewStateCache.get(cacheId) || {
          updateLastViewed: null,
        };

        WhatsNewReader.getWhatsNewContents().then(function(result) {
          if (result) {
            $scope.fileContents = renderer.render(parser.parse(result.contents));
            $scope.fileLastUpdated = result.lastUpdated;
            $scope.lastUpdatedDate = new Date(result.lastUpdated).getTime();
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
      },
    ],
  };
});
