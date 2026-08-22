import { shallow } from 'enzyme';
import React from 'react';

import { IPRangeRules } from '@spinnaker/amazon';
import { CollapsibleSection } from '@spinnaker/core';

import { EcsSecurityGroupDetailsComponent as EcsSecurityGroupDetails } from './EcsSecurityGroupDetails';

const tick = () => new Promise((resolve) => setTimeout(resolve));

describe('EcsSecurityGroupDetails', () => {
  const resolvedSecurityGroup = {
    accountId: 'test-account',
    name: 'web-sg',
    provider: 'ecs',
    region: 'eu-west-1',
    vpcId: 'vpc-1',
  };

  function app(isStandalone = false) {
    return {
      isStandalone,
      getDataSource: () => ({
        ready: () => Promise.resolve(),
        onRefresh: () => jasmine.createSpy('unsubscribe'),
      }),
    } as any;
  }

  it('replaces missing details through the injected state service', () => {
    const stateService = { go: jasmine.createSpy('go') };
    const component = new EcsSecurityGroupDetails({
      app: app(),
      resolvedSecurityGroup,
      router: {},
      stateParams: {},
      stateService,
    } as any);

    (component as any).showNotFound();

    expect(stateService.go).toHaveBeenCalledWith('^', { allowModalToStayOpen: true }, { location: 'replace' });
  });

  function securityGroup(name = 'web-sg') {
    return {
      accountId: 'test-account',
      accountName: 'test-account',
      description: 'Web ingress',
      id: `id-${name}`,
      ipRangeRules: [
        {
          protocol: 'tcp',
          range: { ip: '10.0.0.0', cidr: '/24' },
          portRanges: [{ startPort: 80, endPort: 80 }],
        },
      ],
      name,
      region: 'eu-west-1',
      securityGroupRules: [
        {
          protocol: 'tcp',
          portRanges: [{ startPort: 443, endPort: 443 }],
          securityGroup: {
            accountName: 'shared-account',
            id: 'sg-source',
            name: 'source-sg',
            region: 'eu-west-1',
            vpcId: 'vpc-1',
          },
        },
      ],
      vpcId: 'vpc-1',
    };
  }

  it('loads full details and renders descriptions, VPC names, and Amazon rule models with ECS links', async () => {
    const details = securityGroup();
    const securityGroupReader = {
      getApplicationSecurityGroup: jasmine.createSpy('getApplicationSecurityGroup').and.returnValue({}),
      getSecurityGroupDetails: jasmine.createSpy('getSecurityGroupDetails').and.returnValue(Promise.resolve(details)),
    };
    const vpcReader = {
      getVpcName: jasmine.createSpy('getVpcName').and.returnValue(Promise.resolve('Production VPC')),
    };
    const wrapper = shallow(
      <EcsSecurityGroupDetails
        app={app()}
        resolvedSecurityGroup={resolvedSecurityGroup}
        securityGroupReader={securityGroupReader as any}
        vpcReader={vpcReader}
      />,
    );

    await tick();
    wrapper.update();

    expect(securityGroupReader.getSecurityGroupDetails).toHaveBeenCalledWith(
      jasmine.anything(),
      'test-account',
      'ecs',
      'eu-west-1',
      'vpc-1',
      'web-sg',
    );
    expect(vpcReader.getVpcName).toHaveBeenCalledWith('vpc-1');
    const detailsSection = shallow(<div>{wrapper.find(CollapsibleSection).first().prop('children')}</div>);
    expect(detailsSection.text()).toContain('Web ingress');
    expect(detailsSection.text()).toContain('Production VPC');
    expect(wrapper.find(IPRangeRules).prop('ipRules')).toEqual([
      {
        address: '10.0.0.0/24',
        rules: [{ description: '', endPort: 80, protocol: 'tcp', startPort: 80 }],
      },
    ]);

    const referencedRules = shallow(<div>{wrapper.find(CollapsibleSection).last().prop('children')}</div>);
    expect(referencedRules.text()).toContain('tcp: 443');
    const securityGroupLink = referencedRules.find('UISref');
    expect(shallow(<div>{securityGroupLink.prop('children')}</div>).text()).toContain('source-sg (sg-source)');
    expect(securityGroupLink.prop('params')).toEqual(jasmine.objectContaining({ provider: 'ecs', name: 'source-sg' }));
  });

  it('renders explicit empty states when no IP or referenced security-group rules exist', async () => {
    const details = { ...securityGroup(), ipRangeRules: [], securityGroupRules: [] };
    const securityGroupReader = {
      getApplicationSecurityGroup: () => ({}),
      getSecurityGroupDetails: () => Promise.resolve(details),
    };
    const wrapper = shallow(
      <EcsSecurityGroupDetails
        app={app()}
        resolvedSecurityGroup={resolvedSecurityGroup}
        securityGroupReader={securityGroupReader as any}
        vpcReader={{ getVpcName: () => Promise.resolve(null) }}
      />,
    );

    await tick();
    wrapper.update();

    expect(wrapper.find(IPRangeRules).prop('ipRules')).toEqual([]);
    expect(wrapper.find('[data-test-id="ecs-ip-rules-empty"]').text()).toBe('None');
    expect(wrapper.find('[data-test-id="ecs-security-group-rules-empty"]').text()).toBe('None');
  });

  it('shows the standalone not-found state for empty and failed detail loads', async () => {
    const emptyReader = {
      getSecurityGroupDetails: () => Promise.resolve({}),
    };
    const failedReader = {
      getSecurityGroupDetails: () => Promise.reject(new Error('not found')),
    };
    const emptyWrapper = shallow(
      <EcsSecurityGroupDetails
        app={app(true)}
        resolvedSecurityGroup={resolvedSecurityGroup}
        securityGroupReader={emptyReader as any}
      />,
    );
    const failedWrapper = shallow(
      <EcsSecurityGroupDetails
        app={app(true)}
        resolvedSecurityGroup={resolvedSecurityGroup}
        securityGroupReader={failedReader as any}
      />,
    );

    await tick();
    emptyWrapper.update();
    failedWrapper.update();

    expect(emptyWrapper.text()).toContain('Could not find');
    expect(emptyWrapper.text()).toContain('web-sg');
    expect(failedWrapper.text()).toContain('Could not find');
    expect(failedWrapper.text()).toContain('web-sg');
  });

  it('ignores stale detail and VPC responses after coordinates change', async () => {
    let resolveFirstDetails: (details: any) => void;
    let resolveFirstVpc: (name: string) => void;
    const firstDetails = new Promise<any>((resolve) => (resolveFirstDetails = resolve));
    const firstVpc = new Promise<string>((resolve) => (resolveFirstVpc = resolve));
    const securityGroupReader = {
      getApplicationSecurityGroup: () => ({}),
      getSecurityGroupDetails: jasmine
        .createSpy('getSecurityGroupDetails')
        .and.callFake((_app: any, _account: string, _provider: string, _region: string, _vpcId: string, name: string) =>
          name === 'web-sg' ? firstDetails : Promise.resolve({ ...securityGroup('api-sg'), vpcId: 'vpc-2' }),
        ),
    };
    const vpcReader = {
      getVpcName: jasmine
        .createSpy('getVpcName')
        .and.callFake((vpcId: string) => (vpcId === 'vpc-1' ? firstVpc : Promise.resolve('API VPC'))),
    };
    const wrapper = shallow(
      <EcsSecurityGroupDetails
        app={app()}
        resolvedSecurityGroup={resolvedSecurityGroup}
        securityGroupReader={securityGroupReader as any}
        vpcReader={vpcReader}
      />,
    );

    await tick();
    resolveFirstDetails(securityGroup());
    await tick();
    wrapper.setProps({
      resolvedSecurityGroup: { ...resolvedSecurityGroup, name: 'api-sg', vpcId: 'vpc-2' },
    });
    await tick();
    wrapper.update();
    expect(wrapper.text()).toContain('api-sg');
    expect(shallow(<div>{wrapper.find(CollapsibleSection).first().prop('children')}</div>).text()).toContain('API VPC');

    resolveFirstVpc('Stale VPC');
    await tick();
    wrapper.update();

    expect(wrapper.text()).toContain('api-sg');
    expect(wrapper.text()).not.toContain('Stale VPC');
  });

  it('does not update state when a detail request resolves after unmount', async () => {
    let resolveDetails: (details: any) => void;
    const securityGroupReader = {
      getSecurityGroupDetails: () => new Promise<any>((resolve) => (resolveDetails = resolve)),
    };
    const component = new EcsSecurityGroupDetails({
      app: app(),
      resolvedSecurityGroup,
      securityGroupReader: securityGroupReader as any,
    });
    spyOn(component, 'setState');

    (component as any).loadSecurityGroup();
    (component.setState as jasmine.Spy).calls.reset();
    component.componentWillUnmount();
    resolveDetails(securityGroup());
    await tick();

    expect(component.setState).not.toHaveBeenCalled();
  });
});
