import type { Mock } from 'vitest';
import { AmazonSecurityGroupDetailsComponent as AmazonSecurityGroupDetails } from './AmazonSecurityGroupDetails';
import { VpcReader } from '../../vpc/VpcReader';

const tick = () => new Promise((resolve) => setTimeout(resolve));

describe('AmazonSecurityGroupDetails', () => {
  it('replaces missing details through the injected state service', () => {
    const stateService = { go: vi.fn() };
    const component = new AmazonSecurityGroupDetails({
      app: { isStandalone: false },
      resolvedSecurityGroup: { accountId: 'test-account', name: 'missing', region: 'us-west-2' },
      router: {},
      stateParams: {},
      stateService,
    } as any);

    (component as any).autoClose();

    expect(stateService.go).toHaveBeenCalledWith('^', { allowModalToStayOpen: true }, { location: 'replace' });
  });

  it('does not update state when the VPC lookup resolves after unmount', async () => {
    const details = {
      accountId: 'test-account',
      id: 'sg-123',
      name: 'test-security-group',
      region: 'us-west-2',
      vpcId: 'vpc-1',
    };
    const securityGroupReader = {
      getApplicationSecurityGroup: vi.fn().mockReturnValue(details),
      getSecurityGroupDetails: vi.fn().mockReturnValue(Promise.resolve(details)),
    };
    let resolveVpcName: (vpcName: string) => void;
    const vpcName = new Promise<string>((resolve) => {
      resolveVpcName = resolve;
    });
    vi.spyOn(VpcReader, 'getVpcName').mockReturnValue(vpcName);
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
    vi.spyOn(component, 'setState').mockReturnValue(undefined);

    (component as any).loadSecurityGroup();
    await tick();
    expect(VpcReader.getVpcName).toHaveBeenCalledWith('vpc-1');
    (component.setState as Mock).mockClear();

    component.componentWillUnmount();
    resolveVpcName('Main VPC');
    await tick();

    expect(component.setState).not.toHaveBeenCalled();
  });
});
