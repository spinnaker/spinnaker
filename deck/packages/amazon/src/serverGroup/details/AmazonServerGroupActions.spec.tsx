import { shallow } from 'enzyme';
import React from 'react';

import type { Application } from '@spinnaker/core';
import { ConfirmationModalService, ManagedMenuItem, ServerGroupWarningMessageService } from '@spinnaker/core';

import type { IAmazonServerGroupView } from '../../domain';
import { AWSProviderSettings } from '../../aws.settings';
import { AmazonServerGroupActionsComponent as AmazonServerGroupActions } from './AmazonServerGroupActions';
import { AmazonRollbackServerGroupModal } from './rollback';

describe('<AmazonServerGroupActions /> rollback integration', () => {
  const originalAdHocInfraWritesEnabled = AWSProviderSettings.adHocInfraWritesEnabled;
  const runtimeServices = {} as any;

  const shallowActions = (component: React.ReactElement) => {
    const wrapper = shallow(component);
    (wrapper.instance() as any).context = { services: runtimeServices };
    return wrapper;
  };

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
      serverGroups: { refresh: jasmine.createSpy('refresh') },
    } as any);

  const action = (wrapper: ReturnType<typeof shallow>, label: string) =>
    wrapper.find(ManagedMenuItem).filterWhere((item) => item.prop('children') === label);

  beforeEach(() => {
    AWSProviderSettings.adHocInfraWritesEnabled = true;
  });

  afterEach(() => {
    AWSProviderSettings.adHocInfraWritesEnabled = originalAdHocInfraWritesEnabled;
  });

  it('renders standalone Rollback as a managed action and opens the modal with exact enriched state', () => {
    const selected = buildServerGroup({ isDisabled: true, name: 'test-app-main-v001' });
    const rollbackSource = buildServerGroup();
    const unrelated = buildServerGroup({ app: 'other-app', cluster: 'other-app-main', name: 'other-app-main-v001' });
    const application = buildApplication([selected, rollbackSource, unrelated]);
    const show = spyOn(AmazonRollbackServerGroupModal, 'show').and.returnValue(Promise.resolve({} as any));
    const wrapper = shallowActions(<AmazonServerGroupActions app={application} serverGroup={selected} />);

    const rollback = action(wrapper, 'Rollback');
    expect(rollback.length).toBe(1);
    expect(rollback.props()).toEqual(
      jasmine.objectContaining({ application, resource: selected, onClick: jasmine.any(Function) }),
    );

    rollback.prop('onClick')();

    expect(show).toHaveBeenCalledOnceWith(
      {
        allServerGroups: [selected],
        application,
        previousServerGroup: selected,
        serverGroup: rollbackSource,
      },
      runtimeServices,
    );
  });

  it('does not render Rollback when a disabled server group has no enabled rollback source', () => {
    const selected = buildServerGroup({ isDisabled: true, name: 'test-app-main-v001' });
    const wrapper = shallow(<AmazonServerGroupActions app={buildApplication([selected])} serverGroup={selected} />);

    expect(action(wrapper, 'Rollback').length).toBe(0);
  });

  it('opens rollback settings when orchestrated rollback is accepted from Enable', async () => {
    const selected = buildServerGroup({ isDisabled: true, name: 'test-app-main-v001' });
    const rollbackSource = buildServerGroup();
    const application = buildApplication([selected, rollbackSource]);
    const confirm = spyOn(ConfirmationModalService, 'confirm').and.returnValue(Promise.resolve() as any);
    const show = spyOn(AmazonRollbackServerGroupModal, 'show').and.returnValue(Promise.resolve({} as any));
    const wrapper = shallowActions(<AmazonServerGroupActions app={application} serverGroup={selected} />);

    action(wrapper, 'Enable').prop('onClick')();
    await settle();

    expect(confirm).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledOnceWith(
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
    const confirm = spyOn(ConfirmationModalService, 'confirm').and.callFake((params: any) =>
      params.header === 'Rolling back?' ? (Promise.reject({ source: 'footer' }) as any) : (Promise.resolve() as any),
    );
    const show = spyOn(AmazonRollbackServerGroupModal, 'show').and.returnValue(Promise.resolve({} as any));
    const wrapper = shallow(<AmazonServerGroupActions app={application} serverGroup={selected} />);

    action(wrapper, 'Enable').prop('onClick')();
    await settle();

    expect(show).not.toHaveBeenCalled();
    expect(confirm).toHaveBeenCalledTimes(2);
    expect(confirm.calls.mostRecent().args[0]).toEqual(
      jasmine.objectContaining({
        header: `Really enable ${selected.name}?`,
        submitMethod: jasmine.any(Function),
      }),
    );
  });

  it('does nothing when the orchestrated rollback prompt is dismissed', async () => {
    const selected = buildServerGroup({ isDisabled: true, name: 'test-app-main-v001' });
    const rollbackSource = buildServerGroup();
    const application = buildApplication([selected, rollbackSource]);
    const confirm = spyOn(ConfirmationModalService, 'confirm').and.returnValue(
      Promise.reject({ source: 'header' }) as any,
    );
    const show = spyOn(AmazonRollbackServerGroupModal, 'show').and.returnValue(Promise.resolve({} as any));
    const writer = { enableServerGroup: jasmine.createSpy('enableServerGroup') };
    const enable = writer.enableServerGroup;
    const wrapper = shallow(<AmazonServerGroupActions app={application} serverGroup={selected} />);
    (wrapper.instance() as any).context = { services: { serverGroupWriter: writer } };

    action(wrapper, 'Enable').prop('onClick')();
    await settle();

    expect(confirm).toHaveBeenCalledTimes(1);
    expect(show).not.toHaveBeenCalled();
    expect(enable).not.toHaveBeenCalled();
  });

  it('closes destroyed server group details through the injected state service', () => {
    const selected = buildServerGroup();
    const stateService = { go: jasmine.createSpy('go'), includes: jasmine.createSpy('includes').and.returnValue(true) };
    const confirm = spyOn(ConfirmationModalService, 'confirm');
    spyOn(ServerGroupWarningMessageService, 'addDestroyWarningMessage');
    const wrapper = shallow(
      <AmazonServerGroupActions
        app={buildApplication([selected])}
        router={{} as any}
        serverGroup={selected}
        stateParams={{}}
        stateService={stateService as any}
      />,
    );

    action(wrapper, 'Destroy').prop('onClick')();
    confirm.calls.mostRecent().args[0].taskMonitorConfig.onTaskComplete();

    expect(stateService.includes).toHaveBeenCalledWith('**.serverGroup', {
      accountId: 'test-account',
      name: 'test-app-main-v002',
      region: 'us-east-1',
    });
    expect(stateService.go).toHaveBeenCalledWith('^');
  });
});

const settle = () => new Promise((resolve) => setTimeout(resolve));
