import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';

import { AWSProviderSettings } from '@spinnaker/amazon';
import {
  ConfirmationModalService,
  DeckRuntimeContext,
  EntityTagEditor,
  SETTINGS,
  ServerGroupWarningMessageService,
} from '@spinnaker/core';

import { EcsServerGroupActionsComponent as EcsServerGroupActions } from './EcsServerGroupActions';
import { EcsResizeServerGroupModal } from './resize/EcsResizeServerGroupModal';
import { EcsRollbackServerGroupModal } from './rollback/EcsRollbackServerGroupModal';

describe('<EcsServerGroupActions />', () => {
  const originalAdHocInfraWritesEnabled = AWSProviderSettings.adHocInfraWritesEnabled;
  let runtimeServices: any;
  const RuntimeWrapper = ({ children }: React.PropsWithChildren<{}>) => (
    <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>{children}</DeckRuntimeContext.Provider>
  );
  const renderActions = (component: React.ReactElement) => render(<RuntimeWrapper>{component}</RuntimeWrapper>);
  const openActions = () => fireEvent.click(screen.getByRole('button', { name: 'Server Group Actions' }));

  const buildServerGroup = (overrides: any = {}) => ({
    account: 'test-account',
    capacity: { desired: 2, max: 4, min: 1 },
    cloudProvider: 'ecs',
    cluster: 'test-app-main',
    isDisabled: false,
    moniker: { app: 'test-app', cluster: 'test-app-main' },
    name: 'test-app-main-v002',
    region: 'us-east-1',
    runningTasks: [],
    ...overrides,
  });

  const buildApp = (overrides: any = {}) => {
    const serverGroups = overrides.serverGroups || [];
    return {
      attributes: {},
      getDataSource: (key: string) => (key === 'serverGroups' ? { data: serverGroups } : undefined),
      name: 'test-app',
      serverGroups: { data: serverGroups, refresh: vi.fn() },
      ...overrides,
    } as any;
  };

  beforeEach(() => {
    AWSProviderSettings.adHocInfraWritesEnabled = true;
    runtimeServices = {
      serverGroupWriter: {
        destroyServerGroup: () => Promise.resolve(),
        disableServerGroup: () => Promise.resolve(),
        enableServerGroup: () => Promise.resolve(),
      },
    };
  });

  afterEach(() => {
    AWSProviderSettings.adHocInfraWritesEnabled = originalAdHocInfraWritesEnabled;
    SETTINGS.resetToOriginal();
  });

  it('shows rollback, resize, disable, and destroy for an enabled server group', () => {
    const serverGroup = buildServerGroup();
    renderActions(<EcsServerGroupActions app={buildApp({ serverGroups: [serverGroup] })} serverGroup={serverGroup} />);
    openActions();

    expect(screen.getByRole('menuitem', { name: 'Rollback' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'Resize' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'Disable' })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: 'Enable' })).not.toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'Destroy' })).toBeInTheDocument();
  });

  it('shows resize, enable, and destroy for a disabled server group', () => {
    const serverGroup = buildServerGroup({ isDisabled: true });
    renderActions(<EcsServerGroupActions app={buildApp({ serverGroups: [serverGroup] })} serverGroup={serverGroup} />);
    openActions();

    expect(screen.queryByRole('menuitem', { name: 'Rollback' })).not.toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'Resize' })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: 'Disable' })).not.toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'Enable' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'Destroy' })).toBeInTheDocument();
  });

  it('locks enable while a resize task is running', () => {
    const serverGroup = buildServerGroup({
      isDisabled: true,
      runningTasks: [{ execution: { stages: [{ type: 'resizeServerGroup' }] } }],
    });
    renderActions(<EcsServerGroupActions app={buildApp()} serverGroup={serverGroup} />);
    openActions();

    expect(screen.queryByRole('menuitem', { name: 'Enable' })).not.toBeInTheDocument();
    expect(screen.getByText(/Enable/).closest('li')).toHaveClass('disabled');
  });

  it('hides all actions when AWS ad-hoc infrastructure writes are disabled', () => {
    const app = buildApp();
    const serverGroup = buildServerGroup();
    const rendered = renderActions(<EcsServerGroupActions app={app} serverGroup={serverGroup} />);
    expect(screen.getByRole('button', { name: 'Server Group Actions' })).toBeInTheDocument();

    AWSProviderSettings.adHocInfraWritesEnabled = false;

    rendered.rerender(
      <RuntimeWrapper>
        <EcsServerGroupActions app={app} serverGroup={serverGroup} />
      </RuntimeWrapper>,
    );

    expect(screen.queryByRole('button', { name: 'Server Group Actions' })).not.toBeInTheDocument();
  });

  it('protects every write action with the managed-resource interstitial', async () => {
    const app = buildApp();
    const serverGroup = buildServerGroup({
      isManaged: true,
      managedResourceSummary: {
        id: 'managed-server-group',
        isPaused: false,
        locations: { account: 'test-account', regions: [{ name: 'us-east-1' }] },
      },
    });
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockRejectedValue(new Error('cancelled'));
    const rollback = vi.spyOn(EcsRollbackServerGroupModal, 'show');
    const resize = vi.spyOn(EcsResizeServerGroupModal, 'show');
    for (const label of ['Rollback', 'Resize', 'Disable', 'Destroy']) {
      const rendered = renderActions(<EcsServerGroupActions app={app} serverGroup={serverGroup} />);
      openActions();
      fireEvent.click(screen.getByRole('menuitem', { name: label }));
      await waitFor(() => expect(confirm).toHaveBeenCalledOnce());
      expect(confirm.mock.lastCall[0]).toEqual(
        expect.objectContaining({ account: 'test-account', header: 'Pause Management?' }),
      );
      confirm.mockClear();
      rendered.unmount();
    }
    expect(rollback).not.toHaveBeenCalled();
    expect(resize).not.toHaveBeenCalled();
  });

  it('opens the completed rollback modal with the enriched server group', async () => {
    const app = buildApp();
    const serverGroup = buildServerGroup();
    const show = vi.spyOn(EcsRollbackServerGroupModal, 'show').mockReturnValue(Promise.resolve({} as any));
    renderActions(<EcsServerGroupActions app={app} serverGroup={serverGroup} />);

    openActions();
    fireEvent.click(screen.getByRole('menuitem', { name: 'Rollback' }));

    await waitFor(() =>
      expect(show).toHaveBeenCalledExactlyOnceWith({ application: app, serverGroup }, runtimeServices),
    );
  });

  it('opens the completed resize modal with the enriched server group', async () => {
    const app = buildApp();
    const serverGroup = buildServerGroup();
    const show = vi.spyOn(EcsResizeServerGroupModal, 'show').mockReturnValue(Promise.resolve({} as any));
    renderActions(<EcsServerGroupActions app={app} serverGroup={serverGroup} />);

    openActions();
    fireEvent.click(screen.getByRole('menuitem', { name: 'Resize' }));

    await waitFor(() =>
      expect(show).toHaveBeenCalledExactlyOnceWith({ application: app, serverGroup }, runtimeServices),
    );
  });

  ['Disable', 'Enable', 'Destroy'].forEach((label) => {
    it(`confirms ${label.toLowerCase()} with reason and account verification and preserves the exact writer contract`, async () => {
      const app = buildApp();
      const serverGroup = buildServerGroup({ isDisabled: label === 'Enable' });
      const writerMethod = `${label.toLowerCase()}ServerGroup`;
      const writer = runtimeServices.serverGroupWriter as any;
      const write = vi.spyOn(writer, writerMethod).mockReturnValue(Promise.resolve());
      const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(Promise.resolve());
      const stateService = {
        go: vi.fn(),
        includes: vi.fn().mockReturnValue(true),
      };
      vi.spyOn(ServerGroupWarningMessageService, 'addDisableWarningMessage').mockReturnValue(undefined);
      vi.spyOn(ServerGroupWarningMessageService, 'addDestroyWarningMessage').mockReturnValue(undefined);
      renderActions(
        <EcsServerGroupActions
          app={app}
          router={{} as any}
          serverGroup={serverGroup}
          stateParams={{}}
          stateService={stateService as any}
        />,
      );

      openActions();
      fireEvent.click(screen.getByRole('menuitem', { name: label }));

      await waitFor(() => expect(confirm).toHaveBeenCalledOnce());
      const params = confirm.mock.lastCall[0] as any;
      expect(params.account).toBe('test-account');
      expect(params.askForReason).toBe(true);
      const command = { interestingHealthProviderNames: ['Ecs'], reason: 'because it is safe' };
      params.submitMethod(command);
      expect(write).toHaveBeenCalledExactlyOnceWith(serverGroup, label === 'Disable' ? app.name : app, command);
      if (label === 'Destroy') {
        params.taskMonitorConfig.onTaskComplete();
        expect(stateService.go).toHaveBeenCalledWith('^');
      }
    });
  });

  it('preselects ECS health only when the application requests platform-only health', async () => {
    const app = buildApp({ attributes: { platformHealthOnly: true, platformHealthOnlyShowOverride: true } });
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(Promise.resolve());
    vi.spyOn(ServerGroupWarningMessageService, 'addDisableWarningMessage').mockReturnValue(undefined);
    renderActions(<EcsServerGroupActions app={app} serverGroup={buildServerGroup()} />);

    openActions();
    fireEvent.click(screen.getByRole('menuitem', { name: 'Disable' }));

    await waitFor(() => expect((confirm.mock.lastCall[0] as any).interestingHealthProviderNames).toEqual(['Ecs']));
  });

  it('adds entity tag links using the enriched ECS coordinates and refreshes after updates', () => {
    SETTINGS.feature.entityTags = true;
    const app = buildApp();
    const serverGroup = buildServerGroup();
    const show = vi.spyOn(EntityTagEditor, 'show').mockReturnValue(undefined);
    renderActions(<EcsServerGroupActions app={app} serverGroup={serverGroup} />);
    openActions();
    fireEvent.click(screen.getByText('Add notice'));

    expect(show).toHaveBeenCalledWith(
      expect.objectContaining({
        application: app,
        entityType: 'serverGroup',
        owner: serverGroup,
        ownerOptions: [
          expect.objectContaining({ type: 'serverGroup', owner: serverGroup }),
          expect.objectContaining({
            type: 'cluster',
            owner: { account: 'test-account', cloudProvider: 'ecs', name: 'test-app-main', region: 'us-east-1' },
          }),
          expect.objectContaining({
            type: 'cluster',
            owner: { account: 'test-account', cloudProvider: 'ecs', name: 'test-app-main', region: '*' },
          }),
        ],
      }),
    );

    show.mock.lastCall[0].onUpdate();
    expect(app.serverGroups.refresh).toHaveBeenCalled();
  });

  it('omits entity tag links when the feature is disabled', () => {
    const app = buildApp();
    const serverGroup = buildServerGroup();
    SETTINGS.feature.entityTags = true;
    const rendered = renderActions(<EcsServerGroupActions app={app} serverGroup={serverGroup} />);
    openActions();
    expect(screen.getByText('Add notice')).toBeInTheDocument();

    SETTINGS.feature.entityTags = false;

    rendered.rerender(
      <RuntimeWrapper>
        <EcsServerGroupActions app={app} serverGroup={serverGroup} />
      </RuntimeWrapper>,
    );
    openActions();

    expect(screen.queryByText('Add notice')).not.toBeInTheDocument();
  });
});
