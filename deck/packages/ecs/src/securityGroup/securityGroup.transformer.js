export class EcsSecurityGroupTransformer {
  normalizeSecurityGroup(securityGroup) {
    return Promise.resolve(securityGroup);
  }
}
