import { AmazonSecurityGroupDetails } from './AmazonSecurityGroupDetails';
import { VpcReader } from '../../vpc/VpcReader';

const tick = () => new Promise((resolve) => setTimeout(resolve));

describe('AmazonSecurityGroupDetails', () => {
  it('does not update state when the VPC lookup resolves after unmount', async () => {
    const details = {
      accountId: 'test-account',
      id: 'sg-123',
      name: 'test-security-group',
      region: 'us-west-2',
      vpcId: 'vpc-1',
    };
    const securityGroupReader = {
      getApplicationSecurityGroup: jasmine.createSpy('getApplicationSecurityGroup').and.returnValue(details),
      getSecurityGroupDetails: jasmine.createSpy('getSecurityGroupDetails').and.returnValue(Promise.resolve(details)),
    };
    let resolveVpcName: (vpcName: string) => void;
    const vpcName = new Promise<string>((resolve) => {
      resolveVpcName = resolve;
    });
    spyOn(VpcReader, 'getVpcName').and.returnValue(vpcName);
    const component = new AmazonSecurityGroupDetails({
      app: { isStandalone: false } as any,
      resolvedSecurityGroup: {
        accountId: 'test-account',
        name: 'test-security-group',
        region: 'us-west-2',
        vpcId: 'vpc-1',
      },
      securityGroupReader: securityGroupReader as any,
    });
    spyOn(component, 'setState');

    (component as any).loadSecurityGroup();
    await tick();
    expect(VpcReader.getVpcName).toHaveBeenCalledWith('vpc-1');
    (component.setState as jasmine.Spy).calls.reset();

    component.componentWillUnmount();
    resolveVpcName('Main VPC');
    await tick();

    expect(component.setState).not.toHaveBeenCalled();
  });
});
