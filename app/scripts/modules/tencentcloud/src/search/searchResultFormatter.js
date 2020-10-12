'use strict';

const angular = require('angular');

export const TENCENTCLOUD_SEARCH_SEARCHRESULTFORMATTER = 'spinnaker.tencentcloud.search.searchResultFormatter';
angular.module(TENCENTCLOUD_SEARCH_SEARCHRESULTFORMATTER, []).factory('tencentcloudSearchResultFormatter', function () {
  return {
    securityGroups: function (entry) {},
  };
});
