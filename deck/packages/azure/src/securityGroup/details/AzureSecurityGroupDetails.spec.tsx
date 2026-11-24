import type { Mock } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';

import { AccountService, ConfirmationModalService } from '@spinnaker/core';

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
      getDataSource: () => ({ ready: () => Promise.resolve(), onRefresh: () => vi.fn() }),
      securityGroups: { refresh: vi.fn() },
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

  beforeEach(() => {
    vi.spyOn(AccountService, 'challengeDestructiveActions').mockResolvedValue(false);
  });

  it('closes missing details through the injected state service', () => {
    const stateService = { go: vi.fn() };
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
    const application = app();
    const fetchedSecurityGroup = { ...securityGroup(), account: undefined, accountId: undefined };
    const securityGroupReader = {
      getSecurityGroupDetails: vi.fn().mockReturnValue(Promise.resolve(fetchedSecurityGroup)),
    };
    vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(undefined);
    vi.spyOn(AzureSecurityGroupWriter, 'deleteSecurityGroup').mockResolvedValue({} as any);
    render(
      <AzureSecurityGroupDetails
        app={application}
        resolvedSecurityGroup={resolvedSecurityGroup}
        securityGroupReader={securityGroupReader as any}
      />,
    );

    expect(await screen.findByRole('heading', { name: 'fnord-sg' })).toBeInTheDocument();

    expect(securityGroupReader.getSecurityGroupDetails).toHaveBeenCalledWith(
      expect.anything(),
      'test-account',
      'azure',
      'westus',
      'vnet-1',
      'fnord-sg',
    );
    expect(screen.getByRole('heading', { name: 'Information' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Inbound Rules' })).toBeInTheDocument();
    expect(screen.getByText('allow-web')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Security Group Actions' }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'Delete Security Group' }));
    const expectedSecurityGroup = { ...fetchedSecurityGroup, accountId: 'test-account' };
    expect(ConfirmationModalService.confirm).toHaveBeenCalledWith({
      header: 'Really delete fnord-sg?',
      buttonText: 'Delete fnord-sg',
      account: 'test-account',
      taskMonitorConfig: {
        application,
        title: 'Deleting fnord-sg',
      },
      submitMethod: expect.any(Function),
    });
    (ConfirmationModalService.confirm as Mock).mock.lastCall[0].submitMethod();
    expect(AzureSecurityGroupWriter.deleteSecurityGroup).toHaveBeenCalledWith(expectedSecurityGroup, application, {
      cloudProvider: 'azure',
      vpcId: 'vnet-1',
    });
  });

  it('renders Azure security rules with normalized port and source values', () => {
    render(<AzureSecurityGroupRulesSection securityGroup={securityGroup()} />);
    const row = screen.getByText('allow-web').closest('tr') as HTMLElement;

    expect(row).toHaveTextContent('100');
    expect(row).toHaveTextContent('10.0.0.0/24, 192.168.0.0/24');
    expect(row).toHaveTextContent('*');
    expect(row).toHaveTextContent('80, 443');
    expect(row).toHaveTextContent('Inbound');
  });

  it('renders legacy information fields from the Azure details panel', () => {
    render(
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

    expect(screen.getByText('nsg-resource-id')).toBeInTheDocument();
    expect(screen.getByText('prod-account')).toBeInTheDocument();
    expect(screen.getByText('westus')).toBeInTheDocument();
    expect(screen.getByText('frontend ingress')).toBeInTheDocument();
  });

  it('renders legacy details model fields for security rule source and ports', () => {
    render(
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

    expect(screen.getByText('10.0.0.0/24, 192.168.0.0/24')).toBeInTheDocument();
    expect(screen.getByText('8080, 8443')).toBeInTheDocument();
  });

  it('renders Azure security rules sorted by priority', () => {
    render(
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
    const rows = screen.getAllByRole('row').slice(1);
    const ruleNames = rows.map((row) => within(row).getAllByRole('cell')[1].textContent);

    expect(ruleNames).toEqual(['first', 'second']);
  });

  it('reloads when coordinates change and ignores stale responses', async () => {
    let resolveFirst: (value: any) => void;
    let resolveSecond: (value: any) => void;
    const securityGroupReader = {
      getSecurityGroupDetails: vi
        .fn()
        .mockImplementation(
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
    const rendered = render(
      <AzureSecurityGroupDetails
        app={app()}
        resolvedSecurityGroup={resolvedSecurityGroup}
        securityGroupReader={securityGroupReader as any}
      />,
    );

    await waitFor(() => expect(securityGroupReader.getSecurityGroupDetails).toHaveBeenCalledTimes(1));
    rendered.rerender(
      <AzureSecurityGroupDetails
        app={app()}
        resolvedSecurityGroup={{ ...resolvedSecurityGroup, name: 'other-sg' }}
        securityGroupReader={securityGroupReader as any}
      />,
    );
    await waitFor(() => expect(securityGroupReader.getSecurityGroupDetails).toHaveBeenCalledTimes(2));
    resolveSecond({ ...securityGroup(), name: 'other-sg' });
    expect(await screen.findByRole('heading', { name: 'other-sg' })).toBeInTheDocument();
    resolveFirst(securityGroup());
    await Promise.resolve();

    expect(securityGroupReader.getSecurityGroupDetails.mock.calls.length).toBe(2);
    expect(screen.getByRole('heading', { name: 'other-sg' })).toBeInTheDocument();
  });

  it('auto-closes when the details response is empty', async () => {
    const autoClose = vi.fn();
    const securityGroupReader = {
      getSecurityGroupDetails: vi.fn().mockReturnValue(Promise.resolve({})),
    };

    render(
      <AzureSecurityGroupDetails
        app={app()}
        autoClose={autoClose}
        resolvedSecurityGroup={resolvedSecurityGroup}
        securityGroupReader={securityGroupReader as any}
      />,
    );

    await waitFor(() => expect(autoClose).toHaveBeenCalled());
  });

  it('opens edit, clone, and delete actions', () => {
    vi.spyOn(AzureSecurityGroupModal, 'show').mockReturnValue(undefined);
    vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(undefined);
    vi.spyOn(AzureSecurityGroupWriter, 'deleteSecurityGroup').mockReturnValue(Promise.resolve({} as any));
    render(<AzureSecurityGroupActions app={app()} securityGroup={securityGroup()} />);

    fireEvent.click(screen.getByText('Edit Inbound Rules'));
    fireEvent.click(screen.getByText('Clone Security Group'));
    fireEvent.click(screen.getByText('Delete Security Group'));

    expect(AzureSecurityGroupModal.show).toHaveBeenCalledWith(expect.objectContaining({ mode: 'edit' }));
    expect(AzureSecurityGroupModal.show).toHaveBeenCalledWith(expect.objectContaining({ mode: 'clone' }));
    expect(ConfirmationModalService.confirm).toHaveBeenCalled();
    const confirmArgs = (ConfirmationModalService.confirm as Mock).mock.lastCall[0];
    confirmArgs.submitMethod();
    expect(AzureSecurityGroupWriter.deleteSecurityGroup).toHaveBeenCalledWith(
      securityGroup(),
      expect.anything(),
      expect.objectContaining({ cloudProvider: 'azure', vpcId: 'vnet-1' }),
    );
  });

  it('uses resolved coordinates when deleting fetched details without account identity', () => {
    vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(undefined);
    vi.spyOn(AzureSecurityGroupWriter, 'deleteSecurityGroup').mockReturnValue(Promise.resolve({} as any));
    const fetchedDetails = {
      name: 'fnord-sg',
      region: 'westus',
      securityRules: [],
      vpcId: 'vnet-1',
    } as any;
    render(
      React.createElement(AzureSecurityGroupActions as any, {
        app: app(),
        resolvedSecurityGroup,
        securityGroup: fetchedDetails,
      }),
    );

    fireEvent.click(screen.getByText('Delete Security Group'));

    const confirmArgs = (ConfirmationModalService.confirm as Mock).mock.lastCall[0];
    expect(confirmArgs.account).toBe('test-account');
    confirmArgs.submitMethod();
    expect(AzureSecurityGroupWriter.deleteSecurityGroup).toHaveBeenCalledWith(
      expect.objectContaining({ accountId: 'test-account', name: 'fnord-sg', region: 'westus' }),
      expect.anything(),
      expect.objectContaining({ cloudProvider: 'azure', vpcId: 'vnet-1' }),
    );
  });
});
