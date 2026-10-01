import { act, fireEvent, screen, waitFor } from '@testing-library/react';
import React from 'react';
import { of } from 'rxjs';

import { AccountService, DeckRuntimeContext } from '@spinnaker/core';
import { renderWithRouter } from '../../../../../core/src/utils/testUtils/rtl';

import {
  EditSecurityGroupsModal,
  filterSecurityGroupsForServerGroup,
  isLaunchTemplateBacked,
} from './EditSecurityGroupsModal';

describe('EditSecurityGroupsModal', () => {
  let originalAccounts: typeof AccountService.accounts$;
  let runtimeServices: any;

  const serverGroup = { name: 'deck-main-v001', account: 'test', region: 'us-east-1', vpcId: 'vpc-1' } as any;
  const application = { name: 'deck', serverGroups: { refresh: vi.fn() } } as any;
  const modalProps = { application, serverGroup, closeModal: vi.fn(), dismissModal: vi.fn() };

  beforeEach(() => {
    originalAccounts = AccountService.accounts$;
    AccountService.accounts$ = of([]);
    runtimeServices = {};
  });

  afterEach(() => {
    AccountService.accounts$ = originalAccounts;
  });

  function renderModal(props: any) {
    return renderWithRouter(
      <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>
        <EditSecurityGroupsModal {...props} />
      </DeckRuntimeContext.Provider>,
    );
  }

  it('filters by account, region, and VPC while preserving unresolved attached groups', () => {
    const attached = [
      { id: 'sg-attached', name: 'attached' },
      { id: 'sg-unresolved', name: 'sg-unresolved' },
    ] as any;
    const allGroups = {
      test: {
        aws: {
          'us-east-1': [
            { id: 'sg-attached', name: 'attached', vpcId: 'vpc-1' },
            { id: 'sg-available', name: 'available', vpcId: 'vpc-1' },
            { id: 'sg-wrong-vpc', name: 'wrong-vpc', vpcId: 'vpc-2' },
          ],
          'eu-west-1': [{ id: 'sg-wrong-region', name: 'wrong-region', vpcId: 'vpc-1' }],
        },
      },
      other: { aws: { 'us-east-1': [{ id: 'sg-wrong-account', name: 'wrong-account', vpcId: 'vpc-1' }] } },
    } as any;

    expect(filterSecurityGroupsForServerGroup(allGroups, serverGroup, attached).map((group) => group.id)).toEqual([
      'sg-attached',
      'sg-unresolved',
      'sg-available',
    ]);
  });

  it('treats direct and mixed-instance launch templates as launch-template-backed', () => {
    expect(isLaunchTemplateBacked({ launchTemplate: {} } as any)).toBe(true);
    expect(isLaunchTemplateBacked({ mixedInstancesPolicy: {} } as any)).toBe(true);
    expect(isLaunchTemplateBacked({ launchConfig: {} } as any)).toBe(false);
  });

  it('shows a retryable error without losing selections and recovers after retry', async () => {
    const selected = [{ id: 'sg-attached', name: 'attached' }] as any;
    const allGroups = {
      test: { aws: { 'us-east-1': [{ id: 'sg-available', name: 'available', vpcId: 'vpc-1' }] } },
    };
    const getAllSecurityGroups = vi
      .fn()
      .mockRejectedValueOnce(new Error('inventory unavailable'))
      .mockResolvedValueOnce(allGroups);
    runtimeServices.securityGroupReader = { getAllSecurityGroups };
    renderModal({ ...modalProps, securityGroups: selected });

    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load');
    expect(screen.getByText('attached')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Submit' })).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());

    expect(getAllSecurityGroups).toHaveBeenCalledTimes(2);
    expect(screen.getByText('attached')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Submit' })).toBeEnabled();
    fireEvent.keyDown(screen.getByRole('combobox'), { key: 'ArrowDown', keyCode: 40 });
    expect(screen.getByText('available (sg-available)')).toBeInTheDocument();
  });

  it('does not update state when security-group loading completes after unmount', async () => {
    let finishLoading: (groups: any) => void;
    runtimeServices.securityGroupReader = {
      getAllSecurityGroups: vi.fn().mockReturnValue(new Promise((resolve) => (finishLoading = resolve))),
    };
    const consoleError = vi.spyOn(console, 'error').mockReturnValue(undefined);
    const rendered = renderModal(modalProps);
    rendered.unmount();

    await act(async () => {
      finishLoading!({});
      await Promise.resolve();
    });

    expect(consoleError.mock.calls.join('\n')).not.toContain('unmounted component');
  });

  it('submits selected groups through the writer with mixed-instance launch-template state', async () => {
    const updateSecurityGroups = vi.fn().mockResolvedValue({} as any);
    const selected = [{ id: 'sg-attached', name: 'attached' }] as any;
    runtimeServices.securityGroupReader = { getAllSecurityGroups: vi.fn().mockResolvedValue({}) };
    runtimeServices.serverGroupWriter = { updateSecurityGroups };
    renderModal({ ...modalProps, securityGroups: selected, serverGroup: { ...serverGroup, mixedInstancesPolicy: {} } });
    await waitFor(() => expect(screen.getByRole('button', { name: 'Submit' })).toBeEnabled());

    fireEvent.click(screen.getByRole('button', { name: 'Submit' }));

    expect(updateSecurityGroups).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'deck-main-v001' }),
      selected,
      application,
      true,
    );
  });
});
