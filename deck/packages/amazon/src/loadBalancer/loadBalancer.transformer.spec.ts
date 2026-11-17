// Copyright 2026 spinnaker.io
// SPDX-License-Identifier: Apache-2.0

import { AwsLoadBalancerTransformer } from './loadBalancer.transformer';

describe('AwsLoadBalancerTransformer.convertClassicLoadBalancerForEditing', () => {
  const certificateArn = 'arn:aws:iam::123456789012:server-certificate/my-cert';

  const classicLoadBalancer = (listener: any, vpc: any): any => ({
    name: 'app-frontend',
    region: 'us-east-1',
    account: 'test',
    availabilityZones: [],
    elb: {
      securityGroups: [],
      ...vpc,
      listenerDescriptions: [
        {
          listener: {
            protocol: 'HTTPS',
            loadBalancerPort: 443,
            instanceProtocol: 'HTTP',
            instancePort: 7001,
            ...listener,
          },
          policyNames: [],
        },
      ],
    },
  });

  [
    { desc: 'SDK v2 builder names', listener: { sslCertificateId: certificateArn }, vpc: { vpcId: 'vpc-1' } },
    { desc: 'legacy names', listener: { sslcertificateId: certificateArn }, vpc: { vpcid: 'vpc-1' } },
  ].forEach(({ desc, listener, vpc }) => {
    it(`reads the listener certificate and VPC from ${desc}`, () => {
      const loadBalancer = classicLoadBalancer(listener, vpc);
      const command = AwsLoadBalancerTransformer.convertClassicLoadBalancerForEditing(loadBalancer);

      expect(command.vpcId).toBe('vpc-1');
      expect(command.listeners[0]).toEqual(
        expect.objectContaining({
          sslCertificateId: 'my-cert',
          sslCertificateName: 'my-cert',
          sslCertificateType: 'iam',
        }),
      );
    });
  });
});
