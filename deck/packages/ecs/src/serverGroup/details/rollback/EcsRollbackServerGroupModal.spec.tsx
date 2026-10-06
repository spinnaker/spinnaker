import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import { AccountService, DeckRuntimeContext, ReactModal, TaskReader } from '@spinnaker/core';
import { ModalContext } from '../../../../../core/src/presentation/modal/ModalContext';
import { renderWithRouter } from '../../../../../core/src/utils/testUtils/rtl';

import { EcsRollbackServerGroupModal, getEcsRollbackTargets, validateEcsRollbackValues } from './index';

describe('EcsRollbackServerGroupModal', () => {
  const serverGroup = {
    account: 'test-account',
    capacity: { desired: 4, max: 8, min: 2 },
    cluster: 'fnord-main',
    moniker: { app: 'fnord', cluster: 'fnord-main' },
    name: 'fnord-main-v004',
    region: 'eu-west-1',
  };

  function application(attributes: any = {}) {
    return {
      attributes,
      getDataSource: vi.fn(),
      name: 'fnord',
      serverGroups: {
        data: [
          target('fnord-main-v003'),
          target('fnord-main-v002', { isDisabled: false }),
          target('other-main-v003', { cluster: 'other-main', moniker: { app: 'fnord', cluster: 'other-main' } }),
          target('fnord-main-v003-other-account', { account: 'other-account' }),
          target('fnord-main-v003-other-region', { region: 'us-east-1' }),
          target('fnord-main-v003-other-app', { moniker: { app: 'other', cluster: 'fnord-main' } }),
        ],
        refresh: vi.fn(),
      },
    } as any;
  }

  function target(name: string, overrides: any = {}) {
    return {
      account: 'test-account',
      cluster: 'fnord-main',
      isDisabled: true,
      moniker: { app: 'fnord', cluster: 'fnord-main' },
      name,
      region: 'eu-west-1',
      ...overrides,
    };
  }

  function props(app = application()) {
    return {
      application: app,
      closeModal: vi.fn(),
      dismissModal: vi.fn(),
      serverGroup: serverGroup as any,
    };
  }

  function renderModal(modalProps = props(), runtimeServices: any = {}) {
    return renderWithRouter(
      <ModalContext.Provider value={{ onRequestClose: vi.fn() }}>
        <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>
          <EcsRollbackServerGroupModal {...modalProps} />
        </DeckRuntimeContext.Provider>
      </ModalContext.Provider>,
    );
  }

  beforeEach(() => {
    vi.spyOn(AccountService, 'challengeDestructiveActions').mockResolvedValue(true);
  });

  it('finds only disabled rollback targets in the same application, cluster, account, and region', () => {
    expect(getEcsRollbackTargets(application(), serverGroup as any).map((candidate) => candidate.name)).toEqual([
      'fnord-main-v003',
    ]);
  });

  it('requires a target and a health threshold from 0 through 100 while keeping reason optional', () => {
    expect(validateEcsRollbackValues({ restoreServerGroupName: '', targetHealthyRollbackPercentage: 100 })).toEqual({
      restoreServerGroupName: 'Select a server group to restore',
    });
    expect(
      validateEcsRollbackValues({ restoreServerGroupName: 'fnord-main-v003', targetHealthyRollbackPercentage: -1 }),
    ).toEqual({ targetHealthyRollbackPercentage: 'Healthy threshold must be between 0 and 100' });
    expect(
      validateEcsRollbackValues({ restoreServerGroupName: 'fnord-main-v003', targetHealthyRollbackPercentage: 101 }),
    ).toEqual({ targetHealthyRollbackPercentage: 'Healthy threshold must be between 0 and 100' });
    expect(
      validateEcsRollbackValues({ restoreServerGroupName: 'fnord-main-v003', targetHealthyRollbackPercentage: 95 }),
    ).toEqual({});
    expect(
      validateEcsRollbackValues({ restoreServerGroupName: 'fnord-main-v999', targetHealthyRollbackPercentage: 95 }, [
        'fnord-main-v003',
      ]),
    ).toEqual({ restoreServerGroupName: 'Select an eligible server group to restore' });
  });

  it('does not offer or submit a rollback target outside the eligible disabled set', async () => {
    const rollbackServerGroup = vi.fn();
    renderModal(props(), { serverGroupWriter: { rollbackServerGroup } });

    expect(screen.queryByRole('option', { name: 'fnord-main-v999' })).not.toBeInTheDocument();
    await userEvent.type(screen.getByRole('textbox', { name: 'Confirm account test-account' }), 'test-account');

    expect(screen.getByRole('button', { name: 'Submit' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: 'Submit' }));
    expect(rollbackServerGroup).not.toHaveBeenCalled();
  });

  it('submits the exact shared rollback writer contract through its task monitor', async () => {
    const app = application({ platformHealthOnly: true, platformHealthOnlyShowOverride: true });
    const rollbackServerGroup = vi.fn().mockResolvedValue({ id: 'task-id' });
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockResolvedValue({} as any);
    renderModal(props(app), { serverGroupWriter: { rollbackServerGroup } });

    await userEvent.click(screen.getByRole('combobox', { name: 'Server group to restore' }));
    await userEvent.click(await screen.findByRole('option', { name: 'fnord-main-v003' }));
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Healthy threshold' }), { target: { value: '95' } });
    await userEvent.type(screen.getByRole('textbox', { name: 'Reason' }), '  preserve this reason exactly  ');
    await userEvent.type(screen.getByRole('textbox', { name: 'Confirm account test-account' }), 'test-account');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Submit' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Submit' }));

    expect(rollbackServerGroup).toHaveBeenCalledExactlyOnceWith(serverGroup, app, {
      interestingHealthProviderNames: ['Ecs'],
      platformHealthOnlyShowOverride: true,
      reason: '  preserve this reason exactly  ',
      rollbackContext: {
        restoreServerGroupName: 'fnord-main-v003',
        rollbackServerGroupName: 'fnord-main-v004',
        targetHealthyRollbackPercentage: 95,
      },
      rollbackType: 'EXPLICIT',
    });
    await waitFor(() => expect(app.serverGroups.refresh).toHaveBeenCalledExactlyOnceWith());
  });

  it('renders task, verification, reason, threshold, and ECS platform-health controls', () => {
    const app = application({ platformHealthOnly: true, platformHealthOnlyShowOverride: true });
    renderModal(props(app));

    expect(screen.getByText('Rollback fnord-main-v004')).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Server group to restore' })).toBeInTheDocument();
    expect(screen.getByRole('spinbutton', { name: 'Healthy threshold' })).toHaveValue(100);
    expect(screen.getByRole('textbox', { name: 'Reason' })).toBeInTheDocument();
    expect(screen.getByText(/Type the name of the account/)).toHaveTextContent('test-account');
    expect(screen.getByRole('checkbox', { name: 'Consider only Ecs health' })).toBeChecked();
  });

  it('exports a show primitive for later actions integration', () => {
    const show = vi.spyOn(ReactModal, 'show').mockReturnValue(Promise.resolve() as any);
    const modalProps = props();
    const runtimeServices = {} as any;

    EcsRollbackServerGroupModal.show(modalProps, runtimeServices);

    expect(show).toHaveBeenCalledExactlyOnceWith(EcsRollbackServerGroupModal, modalProps, undefined, runtimeServices);
  });
});
