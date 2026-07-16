import { groupBy } from 'lodash';

import type { ISecurityGroup } from '@spinnaker/core';

import { VpcReader } from '../vpc/VpcReader';

export class AwsSecurityGroupTransformer {
  public supportsCompression = true;

  public normalizeSecurityGroup(securityGroup: ISecurityGroup): PromiseLike<ISecurityGroup> {
    return VpcReader.listVpcs().then((vpcs) => {
      const match = vpcs.find((test) => test.id === securityGroup.vpcId);
      securityGroup.vpcName = match ? match.name : '';
      return securityGroup;
    });
  }

  public compress(securityGroups: ISecurityGroup[]): { [vpcId: string]: string[][] } {
    const grouped = groupBy(securityGroups, 'vpcId') as { [vpcId: string]: ISecurityGroup[] | string[][] };
    Object.keys(grouped).forEach((vpcId) => {
      grouped[vpcId] = (grouped[vpcId] as ISecurityGroup[]).map((g) => [g.name, g.id]);
    });
    return grouped as { [vpcId: string]: string[][] };
  }

  public decompress(groupedGroups: { [vpcId: string]: string[][] }): Array<Partial<ISecurityGroup>> {
    const flattened: Array<Partial<ISecurityGroup>> = [];
    Object.keys(groupedGroups).forEach((vpcId) => {
      groupedGroups[vpcId].forEach((g) => {
        flattened.push({ name: g[0], id: g[1], vpcId });
      });
    });
    return flattened;
  }
}
