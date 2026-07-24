import { mount as enzymeMount } from 'enzyme';
import React from 'react';
import { Dropdown, Tooltip } from 'react-bootstrap';

import { AWSProviderSettings } from '@spinnaker/amazon';
import {
  AddEntityTagLinks,
  ConfirmationModalService,
  DeckRuntimeContext,
  ManagedMenuItem,
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
  const shallow = (component: React.ReactElement) => enzymeMount(component, { wrappingComponent: RuntimeWrapper });

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
      serverGroups: { data: serverGroups, refresh: jasmine.createSpy('refresh') },
      ...overrides,
    } as any;
  };

  const action = (wrapper: any, label: string) =>
    wrapper.find(ManagedMenuItem).filterWhere((item: any) => item.prop('children') === label);

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
    const wrapper = shallow(
      <EcsServerGroupActions app={buildApp({ serverGroups: [serverGroup] })} serverGroup={serverGroup} />,
    );

    expect(action(wrapper, 'Rollback').length).toBe(1);
    expect(action(wrapper, 'Resize').length).toBe(1);
    expect(action(wrapper, 'Disable').length).toBe(1);
    expect(action(wrapper, 'Enable').length).toBe(0);
    expect(action(wrapper, 'Destroy').length).toBe(1);
  });

  it('shows resize, enable, and destroy for a disabled server group', () => {
    const serverGroup = buildServerGroup({ isDisabled: true });
    const wrapper = shallow(
      <EcsServerGroupActions app={buildApp({ serverGroups: [serverGroup] })} serverGroup={serverGroup} />,
    );

    expect(action(wrapper, 'Rollback').length).toBe(0);
    expect(action(wrapper, 'Resize').length).toBe(1);
    expect(action(wrapper, 'Disable').length).toBe(0);
    expect(action(wrapper, 'Enable').length).toBe(1);
    expect(action(wrapper, 'Destroy').length).toBe(1);
  });

  it('locks enable while a resize task is running', () => {
    const serverGroup = buildServerGroup({
      isDisabled: true,
      runningTasks: [{ execution: { stages: [{ type: 'resizeServerGroup' }] } }],
    });
    const wrapper = shallow(<EcsServerGroupActions app={buildApp()} serverGroup={serverGroup} />);

    expect(action(wrapper, 'Enable').length).toBe(0);
    expect(wrapper.find('li.disabled').length).toBe(1);
    expect(wrapper.find(Tooltip).prop('value')).toContain('Cannot enable');
  });

  it('hides all actions when AWS ad-hoc infrastructure writes are disabled', () => {
    const app = buildApp();
    const serverGroup = buildServerGroup();
    expect(shallow(<EcsServerGroupActions app={app} serverGroup={serverGroup} />).find(Dropdown).length).toBe(1);

    AWSProviderSettings.adHocInfraWritesEnabled = false;

    const wrapper = shallow(<EcsServerGroupActions app={app} serverGroup={serverGroup} />);

    expect(wrapper.find(Dropdown).length).toBe(0);
  });

  it('protects every write action with the managed-resource interstitial', () => {
    const app = buildApp();
    const serverGroup = buildServerGroup({ isManaged: true });
    const wrapper = shallow(<EcsServerGroupActions app={app} serverGroup={serverGroup} />);

    expect(wrapper.find(ManagedMenuItem).length).toBe(4);
    wrapper.find(ManagedMenuItem).forEach((item) => {
      expect(item.prop('resource')).toBe(serverGroup);
      expect(item.prop('application')).toBe(app);
    });
  });

  it('opens the completed rollback modal with the enriched server group', () => {
    const app = buildApp();
    const serverGroup = buildServerGroup();
    const show = spyOn(EcsRollbackServerGroupModal, 'show').and.returnValue(Promise.resolve({} as any));
    const wrapper = shallow(<EcsServerGroupActions app={app} serverGroup={serverGroup} />);

    action(wrapper, 'Rollback').prop('onClick')();

    expect(show).toHaveBeenCalledOnceWith({ application: app, serverGroup }, runtimeServices);
  });

  it('opens the completed resize modal with the enriched server group', () => {
    const app = buildApp();
    const serverGroup = buildServerGroup();
    const show = spyOn(EcsResizeServerGroupModal, 'show').and.returnValue(Promise.resolve({} as any));
    const wrapper = shallow(<EcsServerGroupActions app={app} serverGroup={serverGroup} />);

    action(wrapper, 'Resize').prop('onClick')();

    expect(show).toHaveBeenCalledOnceWith({ application: app, serverGroup }, runtimeServices);
  });

  ['Disable', 'Enable', 'Destroy'].forEach((label) => {
    it(`confirms ${label.toLowerCase()} with reason and account verification and preserves the exact writer contract`, () => {
      const app = buildApp();
      const serverGroup = buildServerGroup({ isDisabled: label === 'Enable' });
      const writerMethod = `${label.toLowerCase()}ServerGroup`;
      const writer = runtimeServices.serverGroupWriter as any;
      const write = spyOn(writer, writerMethod).and.returnValue(Promise.resolve());
      const confirm = spyOn(ConfirmationModalService, 'confirm').and.returnValue(Promise.resolve());
      const stateService = {
        go: jasmine.createSpy('go'),
        includes: jasmine.createSpy('includes').and.returnValue(true),
      };
      spyOn(ServerGroupWarningMessageService, 'addDisableWarningMessage');
      spyOn(ServerGroupWarningMessageService, 'addDestroyWarningMessage');
      const wrapper = shallow(
        <EcsServerGroupActions
          app={app}
          router={{} as any}
          serverGroup={serverGroup}
          stateParams={{}}
          stateService={stateService as any}
        />,
      );

      action(wrapper, label).prop('onClick')();

      const params = confirm.calls.mostRecent().args[0] as any;
      expect(params.account).toBe('test-account');
      expect(params.askForReason).toBe(true);
      const command = { interestingHealthProviderNames: ['Ecs'], reason: 'because it is safe' };
      params.submitMethod(command);
      expect(write).toHaveBeenCalledOnceWith(serverGroup, label === 'Disable' ? app.name : app, command);
      if (label === 'Destroy') {
        params.taskMonitorConfig.onTaskComplete();
        expect(stateService.go).toHaveBeenCalledWith('^');
      }
    });
  });

  it('preselects ECS health only when the application requests platform-only health', () => {
    const app = buildApp({ attributes: { platformHealthOnly: true, platformHealthOnlyShowOverride: true } });
    const confirm = spyOn(ConfirmationModalService, 'confirm').and.returnValue(Promise.resolve());
    spyOn(ServerGroupWarningMessageService, 'addDisableWarningMessage');
    const wrapper = shallow(<EcsServerGroupActions app={app} serverGroup={buildServerGroup()} />);

    action(wrapper, 'Disable').prop('onClick')();

    expect((confirm.calls.mostRecent().args[0] as any).interestingHealthProviderNames).toEqual(['Ecs']);
  });

  it('adds entity tag links using the enriched ECS coordinates and refreshes after updates', () => {
    SETTINGS.feature.entityTags = true;
    const app = buildApp();
    const serverGroup = buildServerGroup();
    const wrapper = shallow(<EcsServerGroupActions app={app} serverGroup={serverGroup} />);
    const links = wrapper.find(AddEntityTagLinks);

    expect(links.prop('component')).toBe(serverGroup);
    expect(links.prop('application')).toBe(app);
    expect(links.prop('entityType')).toBe('serverGroup');
    expect(links.prop('ownerOptions')).toEqual([
      jasmine.objectContaining({ type: 'serverGroup', owner: serverGroup }),
      jasmine.objectContaining({
        type: 'cluster',
        owner: { account: 'test-account', cloudProvider: 'ecs', name: 'test-app-main', region: 'us-east-1' },
      }),
      jasmine.objectContaining({
        type: 'cluster',
        owner: { account: 'test-account', cloudProvider: 'ecs', name: 'test-app-main', region: '*' },
      }),
    ]);

    links.prop('onUpdate')();
    expect(app.serverGroups.refresh).toHaveBeenCalled();
  });

  it('omits entity tag links when the feature is disabled', () => {
    const app = buildApp();
    const serverGroup = buildServerGroup();
    SETTINGS.feature.entityTags = true;
    expect(shallow(<EcsServerGroupActions app={app} serverGroup={serverGroup} />).find(AddEntityTagLinks).length).toBe(
      1,
    );

    SETTINGS.feature.entityTags = false;

    const wrapper = shallow(<EcsServerGroupActions app={app} serverGroup={serverGroup} />);

    expect(wrapper.find(AddEntityTagLinks).length).toBe(0);
  });
});
