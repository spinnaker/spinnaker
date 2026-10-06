import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import {
  AccountService,
  DeckRuntimeContext,
  ReactModal,
  ServerGroupWriter,
  TaskExecutor,
  TaskReader,
} from '@spinnaker/core';
import { renderWithRouter } from '../../../../../core/src/utils/testUtils/rtl';

import type { IAmazonServerGroup } from '../../../domain';
import {
  AmazonRollbackServerGroupModal,
  buildAmazonRollbackJob,
  getAmazonPreviousImageServerGroup,
  getAmazonRollbackType,
  getDefaultAmazonHealthyRollbackPercentage,
  validateAmazonRollbackValues,
} from './index';

describe('AmazonRollbackServerGroupModal', () => {
  const serverGroup = {
    account: 'test-account',
    app: 'fnord',
    capacity: { desired: 10, max: 12, min: 2 },
    cluster: 'fnord-main',
    instanceCounts: { total: 10 },
    moniker: { app: 'fnord', cluster: 'fnord-main' },
    name: 'fnord-main-v004',
    region: 'eu-west-1',
    type: 'aws',
  } as IAmazonServerGroup;

  const previousServerGroup = (name = 'fnord-main-v003') => ({ ...serverGroup, isDisabled: true, name });
  const application = (attributes: any = {}) =>
    ({ attributes, getDataSource: vi.fn(), name: 'fnord', serverGroups: { refresh: vi.fn() } } as any);
  const props = (app = application(), overrides: any = {}) => ({
    allServerGroups: [previousServerGroup()],
    application: app,
    closeModal: vi.fn(),
    dismissModal: vi.fn(),
    previousServerGroup: previousServerGroup(),
    serverGroup,
    ...overrides,
  });

  beforeEach(() => vi.spyOn(AccountService, 'challengeDestructiveActions').mockResolvedValue(true));

  it('uses capacity-sensitive healthy percentage defaults', () => {
    expect([0, 9, 10, 19, 20].map(getDefaultAmazonHealthyRollbackPercentage)).toEqual([100, 100, 90, 90, 95]);
  });

  it('normalizes previous-image metadata and selects that mode only without deployed candidates', () => {
    const withPreviousImage = {
      ...serverGroup,
      entityTags: {
        creationMetadata: {
          value: {
            previousServerGroup: {
              buildInfo: { jenkins: { number: 42 } },
              imageId: 'ami-012345',
              imageName: 'fnord-20260712',
              name: 'fnord-main-v003',
            },
          },
        },
      },
    } as any;

    expect(getAmazonPreviousImageServerGroup(withPreviousImage)).toEqual({
      buildNumber: 42,
      imageId: 'ami-012345',
      imageName: 'fnord-20260712',
      name: 'fnord-main-v003',
    });
    expect(getAmazonRollbackType(withPreviousImage, [])).toBe('PREVIOUS_IMAGE');
    expect(getAmazonRollbackType(withPreviousImage, [previousServerGroup()])).toBe('EXPLICIT');
    expect(getAmazonRollbackType(serverGroup, [])).toBe('EXPLICIT');
    expect(
      getAmazonPreviousImageServerGroup({
        ...withPreviousImage,
        entityTags: {
          creationMetadata: {
            value: {
              previousServerGroup: {
                imageId: 'fnord-20260712',
                imageName: 'fnord-20260712',
                name: 'fnord-main-v003',
              },
            },
          },
        },
      } as any),
    ).toEqual({ imageId: undefined, imageName: 'fnord-20260712', name: 'fnord-main-v003' });
  });

  it('requires an eligible explicit target and valid health percentage and delay', () => {
    const valid = {
      delayBeforeDisableSeconds: 0,
      restoreServerGroupName: 'fnord-main-v003',
      targetHealthyRollbackPercentage: 95,
    };
    expect(validateAmazonRollbackValues(valid, 'EXPLICIT', ['fnord-main-v003'])).toEqual({});
    expect(validateAmazonRollbackValues({ ...valid, restoreServerGroupName: undefined }, 'EXPLICIT')).toEqual({
      restoreServerGroupName: 'Select a server group to restore',
    });
    expect(validateAmazonRollbackValues(valid, 'EXPLICIT', ['fnord-main-v002'])).toEqual({
      restoreServerGroupName: 'Select an eligible server group to restore',
    });
    [-1, 101, Number.NaN, Number.POSITIVE_INFINITY].forEach((targetHealthyRollbackPercentage) =>
      expect(validateAmazonRollbackValues({ ...valid, targetHealthyRollbackPercentage }, 'EXPLICIT')).toEqual({
        targetHealthyRollbackPercentage: 'Healthy threshold must be between 0 and 100',
      }),
    );
    [-1, 1.5, Number.NaN, Number.POSITIVE_INFINITY].forEach((delayBeforeDisableSeconds) =>
      expect(validateAmazonRollbackValues({ ...valid, delayBeforeDisableSeconds }, 'EXPLICIT')).toEqual({
        delayBeforeDisableSeconds: 'Delay must be a non-negative whole number',
      }),
    );
    expect(validateAmazonRollbackValues({ ...valid, restoreServerGroupName: undefined }, 'PREVIOUS_IMAGE', [])).toEqual(
      {},
    );
  });

  it('builds the exact rollback writer command', () => {
    expect(
      buildAmazonRollbackJob(application({ platformHealthOnlyShowOverride: true }), serverGroup, 'EXPLICIT', {
        delayBeforeDisableSeconds: 15,
        interestingHealthProviderNames: ['Amazon'],
        reason: '  bad release  ',
        restoreServerGroupName: 'fnord-main-v003',
        targetHealthyRollbackPercentage: 90,
      }),
    ).toEqual({
      interestingHealthProviderNames: ['Amazon'],
      platformHealthOnlyShowOverride: true,
      reason: '  bad release  ',
      rollbackContext: {
        delayBeforeDisableSeconds: 15,
        restoreServerGroupName: 'fnord-main-v003',
        rollbackServerGroupName: 'fnord-main-v004',
        targetHealthyRollbackPercentage: 90,
      },
      rollbackType: 'EXPLICIT',
    });
  });

  it('passes the exact rollback job envelope from the writer to TaskExecutor', () => {
    const app = application();
    const command = buildAmazonRollbackJob(app, serverGroup, 'PREVIOUS_IMAGE', {
      delayBeforeDisableSeconds: 0,
      targetHealthyRollbackPercentage: 90,
    });
    vi.spyOn(TaskExecutor, 'executeTask').mockResolvedValue({} as any);

    new ServerGroupWriter(null).rollbackServerGroup(serverGroup, app, command);

    expect(TaskExecutor.executeTask).toHaveBeenCalledExactlyOnceWith({
      application: app,
      description: 'Rollback Server Group: fnord-main-v004',
      job: [
        {
          cloudProvider: 'aws',
          credentials: 'test-account',
          interestingHealthProviderNames: undefined,
          moniker: serverGroup.moniker,
          platformHealthOnlyShowOverride: undefined,
          reason: undefined,
          region: 'eu-west-1',
          rollbackContext: {
            delayBeforeDisableSeconds: 0,
            restoreServerGroupName: undefined,
            rollbackServerGroupName: 'fnord-main-v004',
            targetHealthyRollbackPercentage: 90,
          },
          rollbackType: 'PREVIOUS_IMAGE',
          type: 'rollbackServerGroup',
        },
      ],
    });
  });

  it('renders the controlled rollback form and submits exact values after verification', async () => {
    const app = application({ platformHealthOnly: true, platformHealthOnlyShowOverride: true });
    const rollbackServerGroup = vi.fn().mockResolvedValue({ id: 'task-id' });
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockResolvedValue({} as any);
    renderWithRouter(
      <DeckRuntimeContext.Provider value={{ services: { serverGroupWriter: { rollbackServerGroup } } } as any}>
        <AmazonRollbackServerGroupModal {...props(app)} />
      </DeckRuntimeContext.Provider>,
    );

    expect(screen.getByRole('combobox', { name: 'Server group to restore' })).toHaveValue('fnord-main-v003');
    expect(screen.getByRole('spinbutton', { name: 'Delay before disabling' })).toHaveValue(0);
    expect(screen.getByRole('spinbutton', { name: 'Target healthy rollback percentage' })).toHaveValue(90);
    expect(screen.getByText(/Type the name of the account/)).toHaveTextContent('test-account');
    expect(screen.getByRole('button', { name: 'Submit' })).toBeDisabled();

    fireEvent.change(screen.getByRole('spinbutton', { name: 'Delay before disabling' }), {
      target: { value: '30' },
    });
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Target healthy rollback percentage' }), {
      target: { value: '95' },
    });
    await userEvent.type(screen.getByRole('textbox', { name: 'Reason' }), 'rollback requested');
    await userEvent.type(screen.getByRole('textbox', { name: 'Confirm account test-account' }), 'test-account');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Submit' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Submit' }));

    expect(rollbackServerGroup).toHaveBeenCalledWith(serverGroup, app, {
      interestingHealthProviderNames: ['Amazon'],
      platformHealthOnlyShowOverride: true,
      reason: 'rollback requested',
      rollbackContext: {
        delayBeforeDisableSeconds: 30,
        restoreServerGroupName: 'fnord-main-v003',
        rollbackServerGroupName: 'fnord-main-v004',
        targetHealthyRollbackPercentage: 95,
      },
      rollbackType: 'EXPLICIT',
    });
    await waitFor(() => expect(app.serverGroups.refresh).toHaveBeenCalledExactlyOnceWith());
  });

  it('dismisses through the public cancel action', async () => {
    const modalProps = props();
    renderWithRouter(
      <DeckRuntimeContext.Provider value={{ services: {} } as any}>
        <AmazonRollbackServerGroupModal {...modalProps} />
      </DeckRuntimeContext.Provider>,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(modalProps.dismissModal).toHaveBeenCalledExactlyOnceWith();
  });

  it('dismisses a submitted task through TaskMonitor close', async () => {
    const modalProps = props();
    const rollbackServerGroup = vi.fn().mockReturnValue(new Promise(() => undefined));
    const cancelPolling = vi.spyOn(TaskReader, 'cancelPolling');
    renderWithRouter(
      <DeckRuntimeContext.Provider value={{ services: { serverGroupWriter: { rollbackServerGroup } } } as any}>
        <AmazonRollbackServerGroupModal {...modalProps} />
      </DeckRuntimeContext.Provider>,
    );

    await userEvent.type(screen.getByRole('textbox', { name: 'Confirm account test-account' }), 'test-account');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Submit' })).toBeEnabled());
    await userEvent.click(screen.getByRole('button', { name: 'Submit' }));
    expect(rollbackServerGroup).toHaveBeenCalledOnce();
    cancelPolling.mockClear();
    await userEvent.click(await screen.findByRole('button', { name: 'Close' }));

    expect(cancelPolling).toHaveBeenCalledExactlyOnceWith(null);
    expect(modalProps.dismissModal).toHaveBeenCalledExactlyOnceWith();
  });

  it('exposes a show primitive for actions integration', () => {
    const show = vi.spyOn(ReactModal, 'show').mockResolvedValue({} as any);
    const modalProps = props();
    const runtimeServices = {} as any;

    AmazonRollbackServerGroupModal.show(modalProps, runtimeServices);

    expect(show).toHaveBeenCalledExactlyOnceWith(
      AmazonRollbackServerGroupModal,
      modalProps,
      undefined,
      runtimeServices,
    );
  });
});
