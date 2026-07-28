export class AzureSecurityGroupReader {
  resolveIndexedSecurityGroup(indexedSecurityGroups, container, securityGroupId) {
    //hack to get around securityGroupId not matching id in indexedSecurityGroups.
    const temp = securityGroupId.split('/');
    return indexedSecurityGroups[container.account][container.region][temp[temp.length - 1]];
  }
}
