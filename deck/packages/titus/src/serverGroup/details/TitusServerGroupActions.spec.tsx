import { shallow } from 'enzyme';
import React from 'react';

import type { IRootScopeService } from 'angular';
import { mock } from 'angular';
import * as core from '@spinnaker/core';

import { TitusServerGroupActionsComponent as TitusServerGroupActions } from './TitusServerGroupActions';
import { TitusRollbackServerGroupModal } from './rollback/TitusRollbackServerGroupModal';

describe('<TitusServerGroupActions />', () => {
  const runtimeServices = {} as any;
  let $rootScope: IRootScopeService;

  beforeEach(
    mock.inject((_$rootScope_: IRootScopeService) => {
      $rootScope = _$rootScope_;
    }),
  );

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
    const serverGroup = buildServerGroup();
    const app = buildApp([previousServerGroup, serverGroup]);
    const show = spyOn(TitusRollbackServerGroupModal, 'show').and.returnValue(Promise.resolve({} as any));
    const wrapper = shallow(<TitusServerGroupActions app={app} serverGroup={serverGroup} />);
    (wrapper.instance() as any).context = { services: runtimeServices };

    rollbackLinks(wrapper).simulate('click');
    $rootScope.$digest();
    await settle();

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
    const serverGroup = buildServerGroup();
    const stateService = { go: jasmine.createSpy('go'), includes: jasmine.createSpy('includes').and.returnValue(true) };
    const confirm = spyOn(core.ConfirmationModalService, 'confirm');
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

    wrapper
      .find('a')
      .filterWhere((link: any) => link.text() === 'Destroy')
      .simulate('click');
    $rootScope.$digest();
    await settle();
    confirm.calls.mostRecent().args[0].taskMonitorConfig.onTaskComplete();

    expect(stateService.go).toHaveBeenCalledWith('^');
  });
});

const settle = () => new Promise((resolve) => setTimeout(resolve));
