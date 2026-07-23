import { shallow } from 'enzyme';
import React from 'react';
import { Subject } from 'rxjs';

import type { Application } from '../../application';
import { CloudProviderRegistry } from '../../cloudProvider';
import { ServerGroupDetails, ServerGroupDetailsComponent } from './ServerGroupDetails';
import { ServerGroupDetailsWrapper } from './ServerGroupDetailsWrapper';

describe('ServerGroupDetailsWrapper', () => {
  const app = { serverGroups: {} } as Application;
  const serverGroup = {
    accountId: 'test',
    name: 'deck-v001',
    provider: 'aws',
    region: 'us-east-1',
  };

  it('renders React server group details when provider React config is available', () => {
    const Actions = () => <button />;
    const Section = () => <div />;
    const detailsGetter = jasmine.createSpy('detailsGetter');
    const component = shallow(<ServerGroupDetailsWrapper app={app} serverGroup={serverGroup} />, {
      disableLifecycleMethods: true,
    });

    component.setState({ Actions, detailsGetter, sections: [Section] });

    expect(component.find(ServerGroupDetails).prop('app')).toBe(app);
    expect(component.find(ServerGroupDetails).prop('serverGroup')).toBe(serverGroup);
    expect(component.find(ServerGroupDetails).prop('Actions')).toBe(Actions);
    expect(component.find(ServerGroupDetails).prop('detailsGetter')).toBe(detailsGetter);
    expect(component.find(ServerGroupDetails).prop('sections')).toEqual([Section]);
  });

  it('renders a migration-required message for legacy template/controller-only server group details', () => {
    const component = shallow(<ServerGroupDetailsWrapper app={app} serverGroup={serverGroup} />, {
      disableLifecycleMethods: true,
    });

    component.setState({ Actions: undefined, detailsGetter: undefined, legacyDetailsConfigured: true, sections: [] });

    expect(component.text()).toContain('Server group details must be migrated to React.');
  });

  it('renders nothing when provider server group details config is missing', () => {
    const component = shallow(<ServerGroupDetailsWrapper app={app} serverGroup={serverGroup} />, {
      disableLifecycleMethods: true,
    });

    component.setState({ Actions: undefined, detailsGetter: undefined, legacyDetailsConfigured: false, sections: [] });

    expect(component.isEmptyRender()).toBe(true);
  });

  it('switches provider configuration before rendering the next server group', async () => {
    const AwsActions = () => <button className="aws-actions" />;
    const AwsSection = () => <div className="aws-section" />;
    const awsGetter = jasmine.createSpy('awsGetter');
    const KubernetesActions = () => <button className="kubernetes-actions" />;
    const KubernetesSection = () => <div className="kubernetes-section" />;
    const kubernetesGetter = jasmine.createSpy('kubernetesGetter');
    const values: Record<string, Record<string, any>> = {
      aws: {
        'serverGroup.detailsActions': AwsActions,
        'serverGroup.detailsGetter': awsGetter,
        'serverGroup.detailsSections': [AwsSection],
      },
      kubernetes: {
        'serverGroup.detailsActions': KubernetesActions,
        'serverGroup.detailsGetter': kubernetesGetter,
        'serverGroup.detailsSections': [KubernetesSection],
      },
    };
    const getValue = spyOn(CloudProviderRegistry, 'getValue').and.callFake(
      (provider: string, key: string) => values[provider]?.[key],
    );
    const component = shallow(<ServerGroupDetailsWrapper app={app} serverGroup={serverGroup} />);
    await Promise.resolve();
    await Promise.resolve();
    component.update();

    expect(component.find(ServerGroupDetails).prop('Actions')).toBe(AwsActions);

    const nextServerGroup = { ...serverGroup, name: 'deck-v002', provider: 'kubernetes' };
    component.setProps({ serverGroup: nextServerGroup });

    expect(component.find(ServerGroupDetails)).toHaveSize(0);
    await Promise.resolve();
    await Promise.resolve();
    component.update();
    expect(getValue.calls.allArgs().filter(([provider]) => provider === 'kubernetes')).toHaveSize(5);
    expect(component.find(ServerGroupDetails).prop('serverGroup')).toBe(nextServerGroup);
    expect(component.find(ServerGroupDetails).prop('Actions')).toBe(KubernetesActions);
    expect(component.find(ServerGroupDetails).prop('detailsGetter')).toBe(kubernetesGetter);
    expect(component.find(ServerGroupDetails).prop('sections')).toEqual([KubernetesSection]);
  });

  it('cancels old provider details when the server group and getter change', () => {
    const oldUpdates = new Subject<any>();
    const newUpdates = new Subject<any>();
    const oldGetter = jasmine.createSpy('oldGetter').and.returnValue(oldUpdates);
    const newGetter = jasmine.createSpy('newGetter').and.returnValue(newUpdates);
    const OldActions = () => <button className="old-actions" />;
    const NewActions = () => <button className="new-actions" />;
    const go = jasmine.createSpy('go');
    const stateService = { go, params: {} };
    const oldProps = {
      Actions: OldActions,
      app: {
        serverGroups: { onRefresh: jasmine.createSpy('onRefresh').and.returnValue(() => undefined) },
      } as any,
      detailsGetter: oldGetter,
      router: {},
      sections: [],
      serverGroup,
      stateParams: {},
      stateService,
    } as any;
    const component = shallow(<ServerGroupDetailsComponent {...oldProps} />, { disableLifecycleMethods: true });
    const instance = component.instance() as ServerGroupDetailsComponent;
    instance.componentDidMount();
    oldUpdates.next({ name: 'old-details', type: 'aws' });
    const oldAutoClose = oldGetter.calls.mostRecent().args[1];

    const nextProps = {
      ...oldProps,
      Actions: NewActions,
      detailsGetter: newGetter,
      serverGroup: { ...serverGroup, name: 'deck-v002', provider: 'kubernetes' },
    };
    instance.componentWillReceiveProps(nextProps);
    component.setProps(nextProps);

    expect(newGetter).toHaveBeenCalledWith(nextProps, jasmine.any(Function));
    expect(component.state()).toEqual({ loading: true, serverGroup: undefined });

    oldUpdates.next({ name: 'stale-details', type: 'aws' });
    expect(component.state('serverGroup')).toBeUndefined();

    const newDetails = { name: 'new-details', type: 'kubernetes' };
    newUpdates.next(newDetails);
    expect(component.find(NewActions).prop('serverGroup')).toBe(newDetails);
    expect(component.find(OldActions)).toHaveSize(0);

    oldAutoClose();
    expect(go).not.toHaveBeenCalled();

    const newAutoClose = newGetter.calls.mostRecent().args[1];
    newAutoClose();
    expect(stateService.params).toEqual({ allowModalToStayOpen: true });
    expect(go).toHaveBeenCalledWith('^', null, { location: 'replace' });

    go.calls.reset();
    instance.componentWillUnmount();
    newAutoClose();
    expect(go).not.toHaveBeenCalled();
  });

  it('keeps the current actions mounted while refreshing the same server group', () => {
    const updates = new Subject<any>();
    const detailsGetter = jasmine.createSpy('detailsGetter').and.returnValue(updates);
    const Actions = () => <button className="actions" />;
    const onRefresh = jasmine.createSpy('onRefresh').and.returnValue(() => undefined);
    const props = {
      Actions,
      app: { serverGroups: { onRefresh } } as any,
      detailsGetter,
      router: {},
      sections: [],
      serverGroup,
      stateParams: {},
      stateService: { go: jasmine.createSpy('go'), params: {} },
    } as any;
    const component = shallow(<ServerGroupDetailsComponent {...props} />, { disableLifecycleMethods: true });
    const instance = component.instance() as ServerGroupDetailsComponent;
    instance.componentDidMount();
    const details = { ...serverGroup, type: 'aws' };
    updates.next(details);

    expect(component.find(Actions)).toHaveSize(1);

    onRefresh.calls.mostRecent().args[1]();

    expect(component.state()).toEqual({ loading: false, serverGroup: details });
    expect(component.find(Actions)).toHaveSize(1);
    instance.componentWillUnmount();
  });
});
