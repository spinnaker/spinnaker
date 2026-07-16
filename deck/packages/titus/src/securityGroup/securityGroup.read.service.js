function resolveIndexedSecurityGroup(indexedSecurityGroups, container, securityGroupId) {
  return indexedSecurityGroups[container.awsAccount][container.region][securityGroupId];
}

export class TitusSecurityGroupReaderDelegate {
  resolveIndexedSecurityGroup = resolveIndexedSecurityGroup;
}

export const TitusSecurityGroupReader = new TitusSecurityGroupReaderDelegate();
