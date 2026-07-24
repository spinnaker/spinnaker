import { mount, shallow } from 'enzyme';
import React from 'react';
import { MenuItem } from 'react-bootstrap';

import { ConfirmationModalService, DeckRuntimeContext, ServerGroupNamePreview, TaskMonitor } from '@spinnaker/core';

import { ServerGroupBasicSettingsComponent } from './configure/wizard/BasicSettings';
import { ServerGroupWizardComponent } from './configure/wizard/serverGroupWizard';
import { CloudrunServerGroupActionsComponent } from './details/CloudrunServerGroupActions';

describe('Cloud Run server group router consumers', () => {
  it('opens the latest server group through the injected state service', () => {
    const go = jasmine.createSpy('go');
    const component = shallow(
      <ServerGroupBasicSettingsComponent
        {...({ router: {}, stateParams: {}, stateService: { go, is: () => true } } as any)}
        accounts={[]}
        app={
          {
            clusters: [],
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

    component.find(ServerGroupNamePreview).prop('navigateToLatestServerGroup')();

    expect(go).toHaveBeenCalledWith('.serverGroup', {
      accountId: 'test',
      provider: 'cloudrun',
      region: 'us',
      serverGroup: 'app-main-v001',
    });
  });

  it('closes destroyed server group details through the injected state service', () => {
    const go = jasmine.createSpy('go');
    const confirm = spyOn(ConfirmationModalService, 'confirm');
    const component = mount(
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

    component.find(MenuItem).first().prop('onClick')();
    confirm.calls.mostRecent().args[0].taskMonitorConfig.onTaskComplete();

    expect(go).toHaveBeenCalledWith('^');
  });

  it('opens a newly created server group through the injected state service', () => {
    spyOn(TaskMonitor, 'modalInstanceEmulation').and.returnValue({ result: Promise.resolve() } as any);
    const go = jasmine.createSpy('go');
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
});
