import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import type { Application } from '@spinnaker/core';
import { ConfirmationModalService, DeckRuntimeContext, ServerGroupWarningMessageService } from '@spinnaker/core';

import type { IAmazonServerGroupView } from '../../domain';
import { AWSProviderSettings } from '../../aws.settings';
import { AmazonServerGroupActionsComponent as AmazonServerGroupActions } from './AmazonServerGroupActions';
import { AmazonRollbackServerGroupModal } from './rollback';

describe('<AmazonServerGroupActions /> rollback integration', () => {
  const originalAdHocInfraWritesEnabled = AWSProviderSettings.adHocInfraWritesEnabled;

  const buildServerGroup = (overrides: Partial<IAmazonServerGroupView> = {}): IAmazonServerGroupView =>
    ({
      account: 'test-account',
      app: 'test-app',
      capacity: { desired: 2, max: 2, min: 0 },
      cloudProvider: 'aws',
      cluster: 'test-app-main',
      createdTime: 2,
      instanceCounts: { total: 2 },
      isDisabled: false,
      name: 'test-app-main-v002',
      moniker: { app: 'test-app', cluster: 'test-app-main' },
      region: 'us-east-1',
      ...overrides,
    } as IAmazonServerGroupView);

  const buildApplication = (serverGroups: IAmazonServerGroupView[]): Application =>
    ({
      attributes: {},
      getDataSource: (key: string) => (key === 'serverGroups' ? { data: serverGroups } : undefined),
      isManagementPaused: false,
      name: 'test-app',
      serverGroups: { refresh: vi.fn() },
    } as any);

  const renderActions = (
    app: Application,
    serverGroup: IAmazonServerGroupView,
    stateService: any = { go: vi.fn(), includes: vi.fn() },
    runtimeServices: any = {},
  ) => ({
    runtimeServices,
    ...render(
      <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>
        <AmazonServerGroupActions
          app={app}
          router={{} as any}
          serverGroup={serverGroup}
          stateParams={{}}
          stateService={stateService}
        />
      </DeckRuntimeContext.Provider>,
    ),
  });

  const openActions = () => userEvent.click(screen.getByRole('button', { name: 'Server Group Actions' }));

  beforeEach(() => {
    AWSProviderSettings.adHocInfraWritesEnabled = true;
  });

  afterEach(() => {
    AWSProviderSettings.adHocInfraWritesEnabled = originalAdHocInfraWritesEnabled;
  });

  it('renders standalone Rollback as a managed action and opens the modal with exact enriched state', async () => {
    const selected = buildServerGroup({ isDisabled: true, name: 'test-app-main-v001' });
    const rollbackSource = buildServerGroup();
    const unrelated = buildServerGroup({ app: 'other-app', cluster: 'other-app-main', name: 'other-app-main-v001' });
    const application = buildApplication([selected, rollbackSource, unrelated]);
    const show = vi.spyOn(AmazonRollbackServerGroupModal, 'show').mockReturnValue(Promise.resolve({} as any));
    const { runtimeServices } = renderActions(application, selected);

    await openActions();
    await userEvent.click(screen.getByText('Rollback'));

    expect(show).toHaveBeenCalledExactlyOnceWith(
      {
        allServerGroups: [selected],
        application,
        previousServerGroup: selected,
        serverGroup: rollbackSource,
      },
      runtimeServices,
    );
  });

  it('does not render Rollback when a disabled server group has no enabled rollback source', async () => {
    const selected = buildServerGroup({ isDisabled: true, name: 'test-app-main-v001' });
    renderActions(buildApplication([selected]), selected);
    await openActions();

    expect(screen.queryByText('Rollback')).not.toBeInTheDocument();
  });

  it('opens rollback settings when orchestrated rollback is accepted from Enable', async () => {
    const selected = buildServerGroup({ isDisabled: true, name: 'test-app-main-v001' });
    const rollbackSource = buildServerGroup();
    const application = buildApplication([selected, rollbackSource]);
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(Promise.resolve() as any);
    const show = vi.spyOn(AmazonRollbackServerGroupModal, 'show').mockReturnValue(Promise.resolve({} as any));
    const { runtimeServices } = renderActions(application, selected);

    await openActions();
    await userEvent.click(screen.getByText('Enable'));
    await settle();

    expect(confirm).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledExactlyOnceWith(
      {
        allServerGroups: [selected],
        application,
        previousServerGroup: selected,
        serverGroup: rollbackSource,
      },
      runtimeServices,
    );
  });

  it('continues to the ordinary Enable confirmation when orchestrated rollback is declined', async () => {
    const selected = buildServerGroup({ isDisabled: true, name: 'test-app-main-v001' });
    const rollbackSource = buildServerGroup();
    const application = buildApplication([selected, rollbackSource]);
    const confirm = vi
      .spyOn(ConfirmationModalService, 'confirm')
      .mockImplementation((params: any) =>
        params.header === 'Rolling back?' ? (Promise.reject({ source: 'footer' }) as any) : (Promise.resolve() as any),
      );
    const show = vi.spyOn(AmazonRollbackServerGroupModal, 'show').mockReturnValue(Promise.resolve({} as any));
    renderActions(application, selected);

    await openActions();
    await userEvent.click(screen.getByText('Enable'));
    await settle();

    expect(show).not.toHaveBeenCalled();
    expect(confirm).toHaveBeenCalledTimes(2);
    expect(confirm.mock.lastCall[0]).toEqual(
      expect.objectContaining({
        header: `Really enable ${selected.name}?`,
        submitMethod: expect.any(Function),
      }),
    );
  });

  it('does nothing when the orchestrated rollback prompt is dismissed', async () => {
    const selected = buildServerGroup({ isDisabled: true, name: 'test-app-main-v001' });
    const rollbackSource = buildServerGroup();
    const application = buildApplication([selected, rollbackSource]);
    let rejectConfirmation: (reason: any) => void;
    const confirmation = new Promise((_, reject) => {
      rejectConfirmation = reject;
    });
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(confirmation as any);
    const show = vi.spyOn(AmazonRollbackServerGroupModal, 'show').mockReturnValue(Promise.resolve({} as any));
    const writer = { enableServerGroup: vi.fn() };
    const enable = writer.enableServerGroup;
    const runtimeServices = { serverGroupWriter: writer } as any;
    renderActions(application, selected, undefined, runtimeServices);

    await openActions();
    await userEvent.click(screen.getByText('Enable'));
    await act(async () => {
      rejectConfirmation({ source: 'header' });
      await confirmation.catch(() => undefined);
    });

    expect(confirm).toHaveBeenCalledTimes(1);
    expect(show).not.toHaveBeenCalled();
    expect(enable).not.toHaveBeenCalled();
  });

  it('closes destroyed server group details through the injected state service', async () => {
    const selected = buildServerGroup();
    const stateService = { go: vi.fn(), includes: vi.fn().mockReturnValue(true) };
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(undefined);
    vi.spyOn(ServerGroupWarningMessageService, 'addDestroyWarningMessage').mockReturnValue(undefined);
    renderActions(buildApplication([selected]), selected, stateService);

    await openActions();
    await userEvent.click(screen.getByText('Destroy'));
    confirm.mock.lastCall[0].taskMonitorConfig.onTaskComplete();

    expect(stateService.includes).toHaveBeenCalledWith('**.serverGroup', {
      accountId: 'test-account',
      name: 'test-app-main-v002',
      region: 'us-east-1',
    });
    expect(stateService.go).toHaveBeenCalledWith('^');
  });
});

const settle = () => act(() => new Promise((resolve) => setTimeout(resolve)));
