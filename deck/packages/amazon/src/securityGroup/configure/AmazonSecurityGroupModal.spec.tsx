import { SecurityGroupWriter, TaskMonitor } from '@spinnaker/core';
import { shallow } from 'enzyme';
import React from 'react';

import {
  AmazonSecurityGroupModal,
  initializeAmazonSecurityGroupForModal,
  isAmazonSecurityGroupValid,
} from './AmazonSecurityGroupModal';

describe('AmazonSecurityGroupModal', () => {
  function inferredSecurityGroup() {
    return {
      accountId: 'target-account-id',
      accountName: 'target-account',
      inboundRules: [
        {
          portRanges: [{ startPort: 443, endPort: 443 }],
          protocol: 'tcp',
          securityGroup: {
            account: 'source-account',
            accountId: 'source-account-id',
            accountName: 'source-account-name',
            id: 'sg-123456',
            inferredName: true,
            name: 'inferred-name',
            vpcId: 'vpc-source',
          },
        },
      ],
      name: 'target-group',
      region: 'us-east-1',
      vpcId: 'vpc-target',
    };
  }

  function buildModal(securityGroup: any, mode = 'edit'): any {
    spyOn(TaskMonitor, 'modalInstanceEmulation').and.returnValue({
      dismiss: () => null,
      result: Promise.resolve(),
    } as any);
    const modal = new AmazonSecurityGroupModal({
      app: { name: 'fnord' },
      closeModal: jasmine.createSpy('closeModal'),
      dismissModal: jasmine.createSpy('dismissModal'),
      mode,
      securityGroup,
    } as any) as any;
    modal.state = {
      ...modal.state,
      securityGroup,
      taskMonitor: { submit: jasmine.createSpy('submit').and.callFake((method: () => any) => method()) },
    };
    modal.setState = (updater: any) => {
      const nextState = typeof updater === 'function' ? updater(modal.state, modal.props) : updater;
      modal.state = { ...modal.state, ...nextState };
    };
    return modal;
  }

  it('preserves inferred source security group identity when initializing edit rules', () => {
    const initialized = initializeAmazonSecurityGroupForModal(
      { mode: 'edit', securityGroup: inferredSecurityGroup() } as any,
      'fnord',
    );

    expect(initialized.securityGroupIngress).toEqual([
      {
        account: 'source-account',
        accountId: 'source-account-id',
        accountName: 'source-account-name',
        endPort: 443,
        existing: true,
        id: 'sg-123456',
        name: null,
        startPort: 443,
        type: 'tcp',
        vpcId: 'vpc-source',
      },
    ]);
  });

  it('accepts existing source security group rules identified by either name or id', () => {
    const securityGroup = initializeAmazonSecurityGroupForModal(
      { mode: 'edit', securityGroup: inferredSecurityGroup() } as any,
      'fnord',
    );
    const rule = securityGroup.securityGroupIngress[0];

    expect(isAmazonSecurityGroupValid(securityGroup)).toBe(true);
    expect(
      isAmazonSecurityGroupValid({
        ...securityGroup,
        securityGroupIngress: [{ ...rule, id: undefined, name: 'named-source-group' }],
      }),
    ).toBe(true);
  });

  it('renders existing identity immutably, allows protocol and port edits, and submits the retained identity', () => {
    spyOn(SecurityGroupWriter, 'upsertSecurityGroup').and.returnValue(Promise.resolve({} as any));
    const securityGroup = initializeAmazonSecurityGroupForModal(
      { mode: 'edit', securityGroup: inferredSecurityGroup() } as any,
      'fnord',
    );
    const modal = buildModal(securityGroup);
    const wrapper = shallow(<div>{modal.renderSecurityGroupRules()}</div>);
    const inputs = wrapper.find('input');

    expect(wrapper.text()).toContain('sg-123456');
    expect(inputs.length).toBe(3);
    expect(inputs.someWhere((input) => input.prop('value') === 'sg-123456')).toBe(false);

    inputs.at(0).simulate('change', { target: { value: 'udp' } });
    inputs.at(1).simulate('change', { target: { value: '8443' } });
    inputs.at(2).simulate('change', { target: { value: '9443' } });
    modal.submit();

    const submittedRule = (SecurityGroupWriter.upsertSecurityGroup as jasmine.Spy).calls.mostRecent().args[0]
      .securityGroupIngress[0];
    expect(submittedRule).toEqual({
      account: 'source-account',
      accountId: 'source-account-id',
      accountName: 'source-account-name',
      endPort: 9443,
      existing: true,
      id: 'sg-123456',
      name: null,
      startPort: 8443,
      type: 'udp',
      vpcId: 'vpc-source',
    });
  });

  it('submits cloned rules by their editable name without stale source identity', () => {
    spyOn(SecurityGroupWriter, 'upsertSecurityGroup').and.returnValue(Promise.resolve({} as any));
    const source = inferredSecurityGroup();
    source.inboundRules[0].securityGroup.inferredName = false;
    source.inboundRules[0].securityGroup.name = 'resolved-source-group';
    const securityGroup = initializeAmazonSecurityGroupForModal(
      { mode: 'clone', securityGroup: source } as any,
      'fnord',
    );
    const modal = buildModal(securityGroup, 'clone');
    const wrapper = shallow(<div>{modal.renderSecurityGroupRules()}</div>);
    const nameInput = wrapper.find('input').at(0);

    expect(nameInput.prop('value')).toBe('resolved-source-group');

    nameInput.simulate('change', { target: { value: 'cloned-source-group' } });
    modal.submit();

    const submittedRule = (SecurityGroupWriter.upsertSecurityGroup as jasmine.Spy).calls.mostRecent().args[0]
      .securityGroupIngress[0];
    expect(submittedRule).toEqual({
      endPort: 443,
      name: 'cloned-source-group',
      startPort: 443,
      type: 'tcp',
    });
  });

  it('requires an inferred source group to be replaced by name when cloning', () => {
    const source = inferredSecurityGroup();
    source.inboundRules[0].securityGroup.name = 'sg-123456';

    const securityGroup = initializeAmazonSecurityGroupForModal(
      { mode: 'clone', securityGroup: source } as any,
      'fnord',
    );

    expect(securityGroup.securityGroupIngress[0].name).toBeNull();
    expect(isAmazonSecurityGroupValid(securityGroup)).toBe(false);
  });
});
