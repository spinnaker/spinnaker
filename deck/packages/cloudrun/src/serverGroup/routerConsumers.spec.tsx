import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import { ConfirmationModalService, DeckRuntimeContext } from '@spinnaker/core';

import { ServerGroupBasicSettingsComponent } from './configure/wizard/BasicSettings';
import { ServerGroupWizardComponent } from './configure/wizard/serverGroupWizard';
import { CloudrunServerGroupActionsComponent } from './details/CloudrunServerGroupActions';

describe('Cloud Run server group router consumers', () => {
  it('opens the latest server group through the injected state service', async () => {
    const go = vi.fn();
    render(
      <ServerGroupBasicSettingsComponent
        {...({ router: {}, stateParams: {}, stateService: { go, is: () => true } } as any)}
        accounts={[]}
        app={
          {
            clusters: [{ name: 'app-main' }],
            name: 'app',
            serverGroups: {
              data: [{ account: 'test', cluster: 'app-main', createdTime: 1, name: 'app-main-v001', region: 'us' }],
            },
          } as any
        }
        detailsChanged={() => undefined}
        formik={
          {
            values: {
              command: {
                credentials: 'test',
                region: 'us',
                selectedProvider: 'cloudrun',
                viewState: { mode: 'create' },
              },
              stack: 'main',
            },
          } as any
        }
        onAccountSelect={() => undefined}
        onEnterStack={() => undefined}
        selectedAccount="test"
      />,
    );

    await userEvent.click(screen.getByText('Go to details for app-main-v001'));

    expect(go).toHaveBeenCalledWith('.serverGroup', {
      accountId: 'test',
      provider: 'cloudrun',
      region: 'us',
      serverGroup: 'app-main-v001',
    });
  });

  it('closes destroyed server group details through the injected state service', async () => {
    const go = vi.fn();
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(undefined);
    render(
      <DeckRuntimeContext.Provider value={{ services: { serverGroupWriter: {} } } as any}>
        <CloudrunServerGroupActionsComponent
          {...({ router: {}, stateParams: {}, stateService: { go, includes: () => true } } as any)}
          app={{ attributes: {} } as any}
          serverGroup={
            {
              account: 'test',
              disabled: true,
              name: 'app-v001',
              region: 'us',
              tags: { isLatest: false },
            } as any
          }
        />
      </DeckRuntimeContext.Provider>,
    );

    await userEvent.click(screen.getByText('Destroy'));
    confirm.mock.lastCall[0].taskMonitorConfig.onTaskComplete();

    expect(go).toHaveBeenCalledWith('^');
  });

  it('opens a newly created server group through the injected state service', () => {
    const go = vi.fn();
    const component = new ServerGroupWizardComponent({
      application: { serverGroups: {} },
      closeModal: () => undefined,
      command: {
        command: { credentials: 'test', region: 'us', viewState: { submitButtonLabel: 'Create' } },
      },
      dismissModal: () => undefined,
      router: {},
      stateParams: {},
      stateService: { go, includes: (state: string) => state === '**.clusters' },
      title: 'Create server group',
    } as any);
    component.state.taskMonitor = {
      task: {
        execution: {
          stages: [{ context: { 'deploy.server.groups': { us: 'app-v001' } }, type: 'cloneServerGroup' }],
        },
      },
    } as any;

    (component as any).onApplicationRefresh();

    expect(go).toHaveBeenCalledWith('.serverGroup', {
      accountId: 'test',
      provider: 'cloudrun',
      region: 'us',
      serverGroup: 'app-v001',
    });
  });

  it('owns the server group refresh subscription across replacement and unmount', () => {
    const firstUnsubscribe = vi.fn();
    const secondUnsubscribe = vi.fn();
    const callbacks: Array<() => void> = [];
    const onNextRefresh = vi.fn().mockImplementation((callback: () => void) => {
      callbacks.push(callback);
      return callbacks.length === 1 ? firstUnsubscribe : secondUnsubscribe;
    });
    const refresh = vi.fn();
    const go = vi.fn();
    const component = new ServerGroupWizardComponent({
      application: { serverGroups: { onNextRefresh, refresh } },
      closeModal: vi.fn(),
      command: { command: { credentials: 'test', region: 'us', viewState: { submitButtonLabel: 'Create' } } },
      dismissModal: vi.fn(),
      router: {},
      stateParams: {},
      stateService: { go, includes: () => false },
      title: 'Create server group',
    } as any) as any;

    component.onTaskComplete();

    expect(onNextRefresh.mock.invocationCallOrder[0]).toBeLessThan(refresh.mock.invocationCallOrder[0]);

    component.onTaskComplete();

    expect(firstUnsubscribe).toHaveBeenCalledTimes(1);

    component.componentWillUnmount();
    callbacks[1]();

    expect(secondUnsubscribe).toHaveBeenCalledTimes(1);
    expect(component.applicationRefreshUnsubscribe).toBeUndefined();
    expect(go).not.toHaveBeenCalled();
  });
});
