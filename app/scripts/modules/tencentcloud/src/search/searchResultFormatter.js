'use strict';

const angular = require('angular');

export const TENCENT_SEARCH_SEARCHRESULTFORMATTER = 'spinnaker.tencentcloud.search.searchResultFormatter';
angular.module(TENCENT_SEARCH_SEARCHRESULTFORMATTER, []).factory('tencentSearchResultFormatter', function() {
  return {
    securityGroups: function(entry) {},
  };
});
