'use strict';

import { module } from 'angular';

export const TENCENTCLOUD_SEARCH_SEARCHRESULTFORMATTER = 'spinnaker.tencentcloud.search.searchResultFormatter';
module(TENCENTCLOUD_SEARCH_SEARCHRESULTFORMATTER, []).factory('tencentcloudSearchResultFormatter', function () {
  return {
    securityGroups: function (entry) {},
  };
});
