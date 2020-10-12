'use strict';

import { module } from 'angular';

import { VpcReader } from '../vpc/VpcReader';

export const AMAZON_SEARCH_SEARCHRESULTFORMATTER = 'spinnaker.amazon.search.searchResultFormatter';
export const name = AMAZON_SEARCH_SEARCHRESULTFORMATTER; // for backwards compatibility
module(AMAZON_SEARCH_SEARCHRESULTFORMATTER, []).factory('awsSearchResultFormatter', function () {
  return {
    securityGroups: function (entry) {
      return VpcReader.getVpcName(entry.vpcId).then(function (vpcName) {
        const region = vpcName ? entry.region + ' - ' + vpcName.toLowerCase() : entry.region;
        return entry.name + ' (' + region + ')';
      });
    },
  };
});
