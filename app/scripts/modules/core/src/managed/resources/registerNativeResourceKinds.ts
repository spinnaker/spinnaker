import { registerResourceKind } from './resourceRegistry';

export const registerNativeResourceKinds = () => {
  registerResourceKind({
    kind: 'titus/cluster@v1',
    iconName: 'cluster',
  });

  registerResourceKind({
    kind: 'ec2/cluster@v1',
    iconName: 'cluster',
  });

  registerResourceKind({
    kind: 'ec2/security-group@v1',
    iconName: 'securityGroup',
  });

  registerResourceKind({
    kind: 'ec2/classic-load-balancer@v1',
    iconName: 'loadBalancer',
  });

  registerResourceKind({
    kind: 'ec2/application-load-balancer@v1',
    iconName: 'loadBalancer',
  });
};
