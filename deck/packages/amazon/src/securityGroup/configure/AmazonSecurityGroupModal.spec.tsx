import type { Mock } from 'vitest';
import { fireEvent, screen, within } from '@testing-library/react';
import React from 'react';

import { SecurityGroupWriter } from '@spinnaker/core';
import { renderWithRouter } from '../../../../core/src/utils/testUtils/rtl';

import {
  AmazonSecurityGroupModalComponent as AmazonSecurityGroupModal,
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

  function renderModal(securityGroup: any, mode: 'edit' | 'clone' = 'edit') {
    return renderWithRouter(
      <AmazonSecurityGroupModal
        {...({
          app: { name: 'fnord' },
          closeModal: vi.fn(),
          dismissModal: vi.fn(),
          mode,
          router: {} as any,
          securityGroup,
          stateParams: {},
          stateService: { go: vi.fn(), includes: vi.fn() },
        } as any)}
      />,
    );
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
    vi.spyOn(SecurityGroupWriter, 'upsertSecurityGroup').mockResolvedValue({} as any);
    const securityGroup = initializeAmazonSecurityGroupForModal(
      { mode: 'edit', securityGroup: inferredSecurityGroup() } as any,
      'fnord',
    );
    renderModal(securityGroup);
    const rules = screen.getByText('Firewall Ingress').parentElement as HTMLElement;

    expect(within(rules).getByText('sg-123456')).toBeInTheDocument();
    expect(within(rules).queryByDisplayValue('sg-123456')).not.toBeInTheDocument();
    fireEvent.change(within(rules).getByDisplayValue('tcp'), { target: { value: 'udp' } });
    const ports = within(rules).getAllByRole('spinbutton');
    fireEvent.change(ports[0], { target: { value: '8443' } });
    fireEvent.change(ports[1], { target: { value: '9443' } });
    fireEvent.click(screen.getByRole('button', { name: 'Update' }));

    const submittedRule = (SecurityGroupWriter.upsertSecurityGroup as Mock).mock.lastCall[0].securityGroupIngress[0];
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
    vi.spyOn(SecurityGroupWriter, 'upsertSecurityGroup').mockResolvedValue({} as any);
    const source = inferredSecurityGroup();
    source.inboundRules[0].securityGroup.inferredName = false;
    source.inboundRules[0].securityGroup.name = 'resolved-source-group';
    const securityGroup = initializeAmazonSecurityGroupForModal(
      { mode: 'clone', securityGroup: source } as any,
      'fnord',
    );
    renderModal(securityGroup, 'clone');

    fireEvent.change(screen.getByDisplayValue('resolved-source-group'), { target: { value: 'cloned-source-group' } });
    fireEvent.click(screen.getByRole('button', { name: 'Clone' }));

    const submittedRule = (SecurityGroupWriter.upsertSecurityGroup as Mock).mock.lastCall[0].securityGroupIngress[0];
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
