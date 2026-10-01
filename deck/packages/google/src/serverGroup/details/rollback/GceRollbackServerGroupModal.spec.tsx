import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';

import { AccountService } from '@spinnaker/core';

import {
  buildGceRollbackJob,
  GceRollbackServerGroupModal,
  getGceRollbackCandidates,
} from './GceRollbackServerGroupModal';

vi.mock('@spinnaker/core', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@spinnaker/core')>();
  const ReactModule = await import('react');
  return {
    ...actual,
    TaskMonitorWrapper: ({ monitor }: any) =>
      ReactModule.createElement(
        'button',
        { onClick: () => monitor.closeModal(), type: 'button' },
        'Close monitored task',
      ),
  };
});

describe('GceRollbackServerGroupModal', () => {
  const application = { name: 'fnord' } as any;
  const serverGroup = {
    account: 'prod',
    app: 'fnord',
    cluster: 'fnord-main',
    name: 'fnord-main-v004',
    region: 'us-central1',
  } as any;

  beforeEach(() => vi.spyOn(AccountService, 'challengeDestructiveActions').mockResolvedValue(true));

  it('keeps only disabled candidates in the same application, cluster, account, and region', () => {
    const eligible = {
      account: 'prod',
      app: 'fnord',
      cluster: 'fnord-main',
      isDisabled: true,
      name: 'fnord-main-v003',
      region: 'us-central1',
    };
    const candidates = [
      eligible,
      { ...eligible, app: 'other', name: 'other-main-v003' },
      { ...eligible, cluster: 'fnord-test', name: 'fnord-test-v003' },
      { ...eligible, account: 'staging', name: 'fnord-main-v003-staging' },
      { ...eligible, region: 'europe-west1', name: 'fnord-main-v003-eu' },
      { ...eligible, isDisabled: false, name: 'fnord-main-v002' },
    ] as any[];

    expect(getGceRollbackCandidates(application, serverGroup, candidates)).toEqual([eligible]);
  });

  it('sorts rollback candidates newest first', () => {
    const candidate = {
      account: 'prod',
      app: 'fnord',
      cluster: 'fnord-main',
      isDisabled: true,
      region: 'us-central1',
    };

    const result = getGceRollbackCandidates(application, serverGroup, [
      { ...candidate, name: 'fnord-main-v001' },
      { ...candidate, name: 'fnord-main-v003' },
      { ...candidate, name: 'fnord-main-v002' },
    ] as any[]);

    expect(result.map(({ name }) => name)).toEqual(['fnord-main-v003', 'fnord-main-v002', 'fnord-main-v001']);
  });

  it('builds the exact explicit rollback job with reason and health override', () => {
    const job = buildGceRollbackJob({ attributes: { platformHealthOnlyShowOverride: true } } as any, serverGroup, {
      interestingHealthProviderNames: ['Google'],
      reason: 'bad release',
      restoreServerGroupName: 'fnord-main-v003',
    });

    expect(job).toEqual({
      interestingHealthProviderNames: ['Google'],
      platformHealthOnlyShowOverride: true,
      reason: 'bad release',
      rollbackContext: {
        restoreServerGroupName: 'fnord-main-v003',
        rollbackServerGroupName: 'fnord-main-v004',
      },
      rollbackType: 'EXPLICIT',
    });
  });

  it('renders reason, account verification, and the Google platform-health override', () => {
    render(
      <GceRollbackServerGroupModal
        application={
          {
            attributes: { platformHealthOnly: true, platformHealthOnlyShowOverride: true },
            name: 'fnord',
          } as any
        }
        dismissModal={vi.fn()}
        serverGroup={serverGroup}
        serverGroups={[]}
        serverGroupWriter={{ rollbackServerGroup: vi.fn() } as any}
      />,
    );

    expect(screen.getByRole('textbox', { name: 'Reason' })).toBeInTheDocument();
    expect(screen.getByText(/Type the name of the account/)).toHaveTextContent('prod');
    expect(screen.getByRole('checkbox', { name: 'Consider only Google health' })).toBeChecked();
  });

  it('submits the exact rollback writer job only after account verification', async () => {
    const applicationWithHealthOverride = {
      attributes: { platformHealthOnly: true, platformHealthOnlyShowOverride: true },
      name: 'fnord',
      serverGroups: { refresh: vi.fn() },
    } as any;
    const candidate = {
      account: 'prod',
      app: 'fnord',
      cluster: 'fnord-main',
      isDisabled: true,
      name: 'fnord-main-v003',
      region: 'us-central1',
    } as any;
    const rollbackServerGroup = vi.fn().mockReturnValue(new Promise(() => undefined));
    render(
      <GceRollbackServerGroupModal
        application={applicationWithHealthOverride}
        dismissModal={vi.fn()}
        serverGroup={serverGroup}
        serverGroups={[candidate]}
        serverGroupWriter={{ rollbackServerGroup }}
      />,
    );
    fireEvent.change(screen.getByRole('combobox', { name: 'Restore to' }), { target: { value: candidate.name } });
    fireEvent.change(screen.getByRole('textbox', { name: 'Reason' }), { target: { value: 'bad release' } });
    expect(screen.getByRole('button', { name: 'Submit' })).toBeDisabled();

    fireEvent.change(screen.getByRole('textbox', { name: 'Confirm account prod' }), { target: { value: 'prod' } });
    await waitFor(() => expect(screen.getByRole('button', { name: 'Submit' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Submit' }));

    expect(rollbackServerGroup).toHaveBeenCalledWith(serverGroup, applicationWithHealthOverride, {
      interestingHealthProviderNames: ['Google'],
      platformHealthOnlyShowOverride: true,
      reason: 'bad release',
      rollbackContext: {
        restoreServerGroupName: 'fnord-main-v003',
        rollbackServerGroupName: 'fnord-main-v004',
      },
      rollbackType: 'EXPLICIT',
    });
  });

  it('dismisses the modal from the task monitor', () => {
    const dismissModal = vi.fn();
    render(
      <GceRollbackServerGroupModal
        application={{ name: 'fnord', serverGroups: { refresh: vi.fn() } } as any}
        dismissModal={dismissModal}
        serverGroup={serverGroup}
        serverGroups={[]}
        serverGroupWriter={{ rollbackServerGroup: vi.fn() } as any}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Close monitored task' }));

    expect(dismissModal).toHaveBeenCalled();
  });
});
