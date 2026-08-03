import { shallow } from 'enzyme';
import React from 'react';
import { MenuItem } from 'react-bootstrap';

import { AccountTag, CollapsibleSection, ConfirmationModalService } from '@spinnaker/core';

import { AzureSecurityGroupModal } from '../configure/AzureSecurityGroupModal';
import { AzureSecurityGroupWriter } from '../securityGroup.write.service';
import {
  AzureSecurityGroupActions,
  AzureSecurityGroupDetailsComponent as AzureSecurityGroupDetails,
  AzureSecurityGroupInformationSection,
  AzureSecurityGroupRulesSection,
} from './AzureSecurityGroupDetails';

describe('AzureSecurityGroupDetails', () => {
  const resolvedSecurityGroup = {
    accountId: 'test-account',
    name: 'fnord-sg',
    provider: 'azure',
    region: 'westus',
    vpcId: 'vnet-1',
  } as any;

  function app() {
    return {
      name: 'fnord',
      getDataSource: () => ({ ready: () => Promise.resolve(), onRefresh: () => jasmine.createSpy('unsubscribe') }),
      securityGroups: { refresh: jasmine.createSpy('refresh') },
    } as any;
  }

  function securityGroup() {
    return {
      account: 'test-account',
      accountId: 'test-account',
      name: 'fnord-sg',
      region: 'westus',
      securityRules: [
        {
          name: 'allow-web',
          access: 'Allow',
          destinationAddressPrefix: '*',
          direction: 'Inbound',
          priority: 100,
          protocol: 'Tcp',
          sourceAddressPrefixes: ['10.0.0.0/24', '192.168.0.0/24'],
          sourcePortRange: '*',
          destinationPortRanges: ['80', '443'],
        },
      ],
      vpcId: 'vnet-1',
    } as any;
  }

  it('closes missing details through the injected state service', () => {
    const stateService = { go: jasmine.createSpy('go') };
    const component = new AzureSecurityGroupDetails({
      app: app(),
      resolvedSecurityGroup,
      router: {},
      stateParams: {},
      stateService,
    } as any);

    (component as any).autoClose();

    expect(stateService.go).toHaveBeenCalledWith('^');
  });

  it('loads details with the core security group reader and renders basic sections', async () => {
    const securityGroupReader = {
      getSecurityGroupDetails: jasmine
        .createSpy('getSecurityGroupDetails')
        .and.returnValue(Promise.resolve(securityGroup())),
    };
    const wrapper = shallow(
      <AzureSecurityGroupDetails
        app={app()}
        resolvedSecurityGroup={resolvedSecurityGroup}
        securityGroupReader={securityGroupReader as any}
      />,
    );

    await Promise.resolve();
    await Promise.resolve();
    wrapper.update();

    expect(securityGroupReader.getSecurityGroupDetails).toHaveBeenCalledWith(
      jasmine.anything(),
      'test-account',
      'azure',
      'westus',
      'vnet-1',
      'fnord-sg',
    );
    expect(wrapper.find('h3').text()).toContain('fnord-sg');
    expect(wrapper.find(AzureSecurityGroupActions).prop('securityGroup')).toEqual(securityGroup());
    expect(wrapper.find(AzureSecurityGroupRulesSection).prop('securityGroup')).toEqual(securityGroup());
  });

  it('renders Azure security rules with normalized port and source values', () => {
    const wrapper = shallow(<AzureSecurityGroupRulesSection securityGroup={securityGroup()} />);
    const sectionContent = shallow(<div>{wrapper.find(CollapsibleSection).prop('children')}</div>);
    const text = sectionContent.text();

    expect(text).toContain('allow-web');
    expect(text).toContain('100');
    expect(text).toContain('10.0.0.0/24, 192.168.0.0/24');
    expect(text).toContain('*');
    expect(text).toContain('80, 443');
    expect(text).toContain('Inbound');
  });

  it('renders legacy information fields from the Azure details panel', () => {
    const wrapper = shallow(
      <AzureSecurityGroupInformationSection
        securityGroup={
          {
            accountName: 'prod-account',
            description: 'frontend ingress',
            id: 'nsg-resource-id',
            region: 'westus',
            vpcId: 'vnet-1',
          } as any
        }
      />,
    );
    const sectionContent = shallow(<div>{wrapper.find(CollapsibleSection).prop('children')}</div>);
    const text = sectionContent.text();

    expect(text).toContain('nsg-resource-id');
    expect(sectionContent.find(AccountTag).prop('account')).toBe('prod-account');
    expect(text).toContain('westus');
    expect(text).toContain('frontend ingress');
  });

  it('renders legacy details model fields for security rule source and ports', () => {
    const wrapper = shallow(
      <AzureSecurityGroupRulesSection
        securityGroup={
          {
            securityRules: [
              {
                name: 'allow-web',
                destinationPortRangeModel: '8080, 8443',
                sourceAddressPrefixModel: '10.0.0.0/24, 192.168.0.0/24',
              },
            ],
          } as any
        }
      />,
    );
    const sectionContent = shallow(<div>{wrapper.find(CollapsibleSection).prop('children')}</div>);
    const text = sectionContent.text();

    expect(text).toContain('10.0.0.0/24, 192.168.0.0/24');
    expect(text).toContain('8080, 8443');
  });

  it('renders Azure security rules sorted by priority', () => {
    const wrapper = shallow(
      <AzureSecurityGroupRulesSection
        securityGroup={
          {
            securityRules: [
              { name: 'second', priority: 200, destinationPortRange: '443', sourceAddressPrefix: '*' },
              { name: 'first', priority: 100, destinationPortRange: '80', sourceAddressPrefix: '*' },
            ],
          } as any
        }
      />,
    );
    const sectionContent = shallow(<div>{wrapper.find(CollapsibleSection).prop('children')}</div>);
    const ruleNames = sectionContent.find('tbody tr').map((row) => row.find('td').at(1).text());

    expect(ruleNames).toEqual(['first', 'second']);
  });

  it('reloads when coordinates change and ignores stale responses', async () => {
    let resolveFirst: (value: any) => void;
    let resolveSecond: (value: any) => void;
    const securityGroupReader = {
      getSecurityGroupDetails: jasmine
        .createSpy('getSecurityGroupDetails')
        .and.callFake(
          (_app: any, _account: string, _provider: string, _region: string, _vpcId: string, name: string) => {
            if (name === 'fnord-sg') {
              return new Promise((resolve) => {
                resolveFirst = resolve;
              });
            }
            return new Promise((resolve) => {
              resolveSecond = resolve;
            });
          },
        ),
    };
    const wrapper = shallow(
      <AzureSecurityGroupDetails
        app={app()}
        resolvedSecurityGroup={resolvedSecurityGroup}
        securityGroupReader={securityGroupReader as any}
      />,
    );

    await Promise.resolve();
    wrapper.setProps({ resolvedSecurityGroup: { ...resolvedSecurityGroup, name: 'other-sg' } });
    await Promise.resolve();
    resolveSecond({ ...securityGroup(), name: 'other-sg' });
    await Promise.resolve();
    resolveFirst(securityGroup());
    await Promise.resolve();
    wrapper.update();

    expect(securityGroupReader.getSecurityGroupDetails.calls.count()).toBe(2);
    expect(wrapper.find('h3').text()).toContain('other-sg');
  });

  it('auto-closes when the details response is empty', async () => {
    const autoClose = jasmine.createSpy('autoClose');
    const securityGroupReader = {
      getSecurityGroupDetails: jasmine.createSpy('getSecurityGroupDetails').and.returnValue(Promise.resolve({})),
    };

    shallow(
      <AzureSecurityGroupDetails
        app={app()}
        autoClose={autoClose}
        resolvedSecurityGroup={resolvedSecurityGroup}
        securityGroupReader={securityGroupReader as any}
      />,
    );

    await Promise.resolve();
    await Promise.resolve();

    expect(autoClose).toHaveBeenCalled();
  });

  it('opens edit, clone, and delete actions', () => {
    spyOn(AzureSecurityGroupModal, 'show');
    spyOn(ConfirmationModalService, 'confirm');
    spyOn(AzureSecurityGroupWriter, 'deleteSecurityGroup').and.returnValue(Promise.resolve({} as any));
    const wrapper = shallow(<AzureSecurityGroupActions app={app()} securityGroup={securityGroup()} />);

    wrapper.find(MenuItem).at(0).prop('onClick')({} as any);
    wrapper.find(MenuItem).at(1).prop('onClick')({} as any);
    wrapper.find(MenuItem).at(2).prop('onClick')({} as any);

    expect(AzureSecurityGroupModal.show).toHaveBeenCalledWith(jasmine.objectContaining({ mode: 'edit' }));
    expect(AzureSecurityGroupModal.show).toHaveBeenCalledWith(jasmine.objectContaining({ mode: 'clone' }));
    expect(ConfirmationModalService.confirm).toHaveBeenCalled();
    const confirmArgs = (ConfirmationModalService.confirm as jasmine.Spy).calls.mostRecent().args[0];
    confirmArgs.submitMethod();
    expect(AzureSecurityGroupWriter.deleteSecurityGroup).toHaveBeenCalledWith(
      securityGroup(),
      jasmine.anything(),
      jasmine.objectContaining({ cloudProvider: 'azure', vpcId: 'vnet-1' }),
    );
  });

  it('uses resolved coordinates when deleting fetched details without account identity', () => {
    spyOn(ConfirmationModalService, 'confirm');
    spyOn(AzureSecurityGroupWriter, 'deleteSecurityGroup').and.returnValue(Promise.resolve({} as any));
    const fetchedDetails = {
      name: 'fnord-sg',
      region: 'westus',
      securityRules: [],
      vpcId: 'vnet-1',
    } as any;
    const wrapper = shallow(
      React.createElement(AzureSecurityGroupActions as any, {
        app: app(),
        resolvedSecurityGroup,
        securityGroup: fetchedDetails,
      }),
    );

    wrapper.find(MenuItem).at(2).prop('onClick')({} as any);

    const confirmArgs = (ConfirmationModalService.confirm as jasmine.Spy).calls.mostRecent().args[0];
    expect(confirmArgs.account).toBe('test-account');
    confirmArgs.submitMethod();
    expect(AzureSecurityGroupWriter.deleteSecurityGroup).toHaveBeenCalledWith(
      jasmine.objectContaining({ accountId: 'test-account', name: 'fnord-sg', region: 'westus' }),
      jasmine.anything(),
      jasmine.objectContaining({ cloudProvider: 'azure', vpcId: 'vnet-1' }),
    );
  });
});
