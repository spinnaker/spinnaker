import { shallow } from 'enzyme';
import React from 'react';
import { act } from 'react-dom/test-utils';

import * as core from '@spinnaker/core';

import { TitusServerGroupActionsComponent as TitusServerGroupActions } from './TitusServerGroupActions';
import { TitusRollbackServerGroupModal } from './rollback/TitusRollbackServerGroupModal';

interface IDeferred<T> {
  promise: Promise<T>;
  resolve: (value: T) => void;
}

function deferred<T>(): IDeferred<T> {
  let resolve: (value: T) => void;
  const promise = new Promise<T>((promiseResolve) => (resolve = promiseResolve));
  return { promise, resolve };
}

describe('<TitusServerGroupActions />', () => {
  const runtimeServices = {} as any;

  const buildServerGroup = (overrides: any = {}) => ({
    account: 'test-account',
    cluster: 'test-app-main',
    createdTime: 2,
    instanceCounts: { total: 2 },
    isDisabled: false,
    name: 'test-app-main-v002',
    region: 'us-east-1',
    zones: ['us-east-1a'],
    ...overrides,
  });

  const buildApp = (serverGroups: any[]) =>
    ({
      attributes: {},
      getDataSource: (key: string) => (key === 'serverGroups' ? { data: serverGroups } : null),
      serverGroups: { refresh: jasmine.createSpy('refresh') },
    } as any);

  const rollbackLinks = (wrapper: any) => wrapper.find('a').filterWhere((link: any) => link.text() === 'Rollback');

  const managedResource = {
    isManaged: true,
    managedResourceSummary: {
      id: 'managed-resource',
      isPaused: false,
      locations: { account: 'test-account' },
    },
  };

  afterEach(() => core.SETTINGS.resetToOriginal());

  it('renders Rollback for an enabled server group', () => {
    const serverGroup = buildServerGroup();
    const wrapper = shallow(<TitusServerGroupActions app={buildApp([serverGroup])} serverGroup={serverGroup} />);

    expect(rollbackLinks(wrapper).length).toBe(1);
  });

  it('renders Rollback for a disabled server group when an enabled group exists in the same cluster', () => {
    const serverGroup = buildServerGroup({ isDisabled: true, name: 'test-app-main-v001' });
    const enabledServerGroup = buildServerGroup({ name: 'test-app-main-v002' });
    const wrapper = shallow(
      <TitusServerGroupActions app={buildApp([serverGroup, enabledServerGroup])} serverGroup={serverGroup} />,
    );

    expect(rollbackLinks(wrapper).length).toBe(1);
  });

  it('opens the Titus rollback modal with the previous server group', async () => {
    const previousServerGroup = buildServerGroup({ createdTime: 1, name: 'test-app-main-v001' });
    const serverGroup = buildServerGroup(managedResource);
    const app = buildApp([previousServerGroup, serverGroup]);
    const managementConfirmation = deferred<any>();
    const rollbackModalShown = deferred<void>();
    spyOn(core.ConfirmationModalService, 'confirm').and.returnValue(managementConfirmation.promise);
    const show = spyOn(TitusRollbackServerGroupModal, 'show').and.callFake(() => {
      rollbackModalShown.resolve(undefined);
      return Promise.resolve({} as any);
    });
    const wrapper = shallow(<TitusServerGroupActions app={app} serverGroup={serverGroup} />);
    (wrapper.instance() as any).context = { services: runtimeServices };

    act(() => {
      rollbackLinks(wrapper).simulate('click');
    });
    expect(show).not.toHaveBeenCalled();

    await act(async () => {
      managementConfirmation.resolve(undefined);
      await rollbackModalShown.promise;
    });

    expect(show).toHaveBeenCalledOnceWith(
      {
        allServerGroups: [previousServerGroup],
        application: app,
        previousServerGroup,
        serverGroup,
      } as any,
      runtimeServices,
    );
  });

  it('does not throw when rollback candidates disappear before click handling', () => {
    const serverGroup = buildServerGroup({ isDisabled: true, name: 'test-app-main-v001' });
    const app = buildApp([serverGroup]);
    const show = spyOn(TitusRollbackServerGroupModal, 'show').and.returnValue(Promise.resolve({} as any));
    const wrapper = shallow(<TitusServerGroupActions app={app} serverGroup={serverGroup} />);

    expect(() => (wrapper.instance() as any).rollbackServerGroup()).not.toThrow();
    expect(show).not.toHaveBeenCalled();
  });

  it('closes destroyed server group details through the injected state service', async () => {
    const serverGroup = buildServerGroup(managedResource);
    const stateService = { go: jasmine.createSpy('go'), includes: jasmine.createSpy('includes').and.returnValue(true) };
    const managementConfirmation = deferred<any>();
    const destroyConfirmationShown = deferred<void>();
    const confirm = spyOn(core.ConfirmationModalService, 'confirm').and.callFake(() => {
      if (confirm.calls.count() === 1) {
        return managementConfirmation.promise;
      }
      destroyConfirmationShown.resolve(undefined);
      return Promise.resolve({} as any);
    });
    spyOn(core.ServerGroupWarningMessageService, 'addDestroyWarningMessage');
    const wrapper = shallow(
      <TitusServerGroupActions
        app={buildApp([serverGroup])}
        router={{} as any}
        serverGroup={serverGroup}
        stateParams={{}}
        stateService={stateService as any}
      />,
    );

    act(() => {
      wrapper
        .find('a')
        .filterWhere((link: any) => link.text() === 'Destroy')
        .simulate('click');
    });
    expect(confirm).toHaveBeenCalledTimes(1);

    await act(async () => {
      managementConfirmation.resolve(undefined);
      await destroyConfirmationShown.promise;
    });
    confirm.calls.mostRecent().args[0].taskMonitorConfig.onTaskComplete();

    expect(stateService.go).toHaveBeenCalledWith('^');
  });
});
