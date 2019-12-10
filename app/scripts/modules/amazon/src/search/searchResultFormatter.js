'use strict';

const angular = require('angular');

import { VpcReader } from '../vpc/VpcReader';

export const AMAZON_SEARCH_SEARCHRESULTFORMATTER = 'spinnaker.amazon.search.searchResultFormatter';
export const name = AMAZON_SEARCH_SEARCHRESULTFORMATTER; // for backwards compatibility
angular.module(AMAZON_SEARCH_SEARCHRESULTFORMATTER, []).factory('awsSearchResultFormatter', function() {
  return {
    securityGroups: function(entry) {
      return VpcReader.getVpcName(entry.vpcId).then(function(vpcName) {
        let region = vpcName ? entry.region + ' - ' + vpcName.toLowerCase() : entry.region;
        return entry.name + ' (' + region + ')';
      });
    },
  };
});
