import { InfrastructureCaches } from '@spinnaker/core';

import { SecurityGroups } from './SecurityGroups';

describe('SecurityGroups', () => {
  it('renders when the security group cache has not been registered', () => {
    vi.spyOn(InfrastructureCaches, 'get').mockReturnValue(undefined);

    expect(() => new SecurityGroups(buildProps() as any)).not.toThrow();
  });
});

function buildProps() {
  return {
    formik: {
      values: {
        credentials: 'test',
        region: 'us-east-1',
        securityGroups: [],
        vpcId: 'vpc-1',
      },
      setFieldValue: vi.fn(),
      validateForm: vi.fn(),
    },
    onLoadingChanged: vi.fn(),
  };
}
