import { module } from 'angular';

import type { ISecurityGroupSearchResult } from '@spinnaker/core';
import type { ISearchResultFormatter } from '@spinnaker/core';

import { VpcReader } from '../vpc/VpcReader';

export const AMAZON_SEARCH_SEARCHRESULTFORMATTER = 'spinnaker.amazon.search.searchResultFormatter';
export const name = AMAZON_SEARCH_SEARCHRESULTFORMATTER; // for backwards compatibility
module(AMAZON_SEARCH_SEARCHRESULTFORMATTER, []).factory('awsSearchResultFormatter', function () {
  return {
    securityGroups: function (entry: ISecurityGroupSearchResult) {
      return VpcReader.getVpcName(entry.vpcId).then(function (vpcName) {
        const region = vpcName ? entry.region + ' - ' + vpcName.toLowerCase() : entry.region;
        return entry.name + ' (' + region + ')';
      });
    } as ISearchResultFormatter,
  };
});
