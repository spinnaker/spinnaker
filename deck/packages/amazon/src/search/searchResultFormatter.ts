import type { ISearchResultFormatter, ISecurityGroupSearchResult } from '@spinnaker/core';

import { VpcReader } from '../vpc/VpcReader';

export const awsSearchResultFormatter: { securityGroups: ISearchResultFormatter } = {
  securityGroups: (entry: ISecurityGroupSearchResult) => {
    return VpcReader.getVpcName(entry.vpcId).then((vpcName) => {
      const region = vpcName ? entry.region + ' - ' + vpcName.toLowerCase() : entry.region;
      return entry.name + ' (' + region + ')';
    });
  },
};
