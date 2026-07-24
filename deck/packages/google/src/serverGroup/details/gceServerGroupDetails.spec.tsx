import { mount as enzymeMount, shallow } from 'enzyme';
import React from 'react';

import { CloudProviderRegistry, CollapsibleSection, DeckRuntimeContext, ManagedMenuItem } from '@spinnaker/core';

import { GceAutoscalingPolicyWriter } from '../../autoscalingPolicy';
import { registerGoogleProvider } from '../../gce.module';
import { GceAutoHealingPolicyDetails } from './autoHealingPolicy';
import { GceAutoscalingPolicyDetails } from './autoscalingPolicy';
import { GceServerGroupActions, gceServerGroupDetailsSections } from './gceServerGroupDetails';
import { GceResizeServerGroupModal } from './resize/GceResizeServerGroupModal';
import { GceRollbackServerGroupModal } from './rollback/GceRollbackServerGroupModal';

describe('GCE server group details integration', () => {
  let runtimeServices: any;
  const RuntimeWrapper = ({ children }: React.PropsWithChildren<{}>) => (
    <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>{children}</DeckRuntimeContext.Provider>
  );
  const mount = (component: React.ReactElement) => enzymeMount(component, { wrappingComponent: RuntimeWrapper });

  beforeEach(() => {
    runtimeServices = {
      serverGroupCommandBuilder: {},
      serverGroupWriter: {},
    };
  });
  const serverGroup = {
    account: 'prod',
    app: 'fnord',
    autoscalingMessages: ['autoscaler active'],
    autoscalingPolicy: { maxNumReplicas: 5, minNumReplicas: 1 },
    autoHealingPolicy: { healthCheck: 'fnord-health-check', initialDelaySec: 300 },
    cluster: 'fnord-main',
    isDisabled: false,
    name: 'fnord-main-v004',
    region: 'us-central1',
  } as any;
  const eligibleRollbackCandidate = {
    ...serverGroup,
    autoscalingPolicy: undefined,
    autoHealingPolicy: undefined,
    isDisabled: true,
    name: 'fnord-main-v003',
  };
  const app = {
    attributes: {},
    name: 'fnord',
    serverGroups: {
      data: [
        eligibleRollbackCandidate,
        { ...eligibleRollbackCandidate, app: 'other', name: 'other-main-v003' },
        { ...eligibleRollbackCandidate, isDisabled: false, name: 'fnord-main-v002' },
      ],
      refresh: jasmine.createSpy('refresh'),
    },
  } as any;

  const managedAction = (wrapper: any, label: string) =>
    wrapper.find(ManagedMenuItem).filterWhere((item: any) => item.prop('children') === label);

  const linkAction = (wrapper: any, label: string) =>
    wrapper.find('a').filterWhere((link: any) => link.text() === label);

  it('registers autoscaling and auto-healing sections backed by the completed details components', () => {
    registerGoogleProvider();

    expect(CloudProviderRegistry.getValue('gce', 'serverGroup.detailsSections')).toEqual(gceServerGroupDetailsSections);

    const renderedSections = gceServerGroupDetailsSections.map((Section) =>
      shallow(<Section app={app} serverGroup={serverGroup} />),
    );
    const autoscaling = renderedSections.find(
      (section) => section.find(CollapsibleSection).prop('heading') === 'Autoscaling',
    );
    const autoHealing = renderedSections.find(
      (section) => section.find(CollapsibleSection).prop('heading') === 'Auto-healing',
    );

    expect(autoscaling.find(GceAutoscalingPolicyDetails).props()).toEqual(
      jasmine.objectContaining({ application: app, policy: serverGroup.autoscalingPolicy, serverGroup }),
    );
    expect(autoHealing.find(GceAutoHealingPolicyDetails).props()).toEqual(
      jasmine.objectContaining({ application: app, policy: serverGroup.autoHealingPolicy, serverGroup }),
    );
  });

  it('keeps policy summaries read-only when GCE ad-hoc infrastructure writes are disabled', () => {
    spyOn(CloudProviderRegistry, 'isDisabled').and.returnValue(true);
    const renderedSections = gceServerGroupDetailsSections.map((Section) =>
      shallow(<Section app={app} serverGroup={serverGroup} />),
    );
    const autoscalingSection = renderedSections.find(
      (section) => section.find(CollapsibleSection).prop('heading') === 'Autoscaling',
    );
    const autoHealingSection = renderedSections.find(
      (section) => section.find(CollapsibleSection).prop('heading') === 'Auto-healing',
    );
    const autoscaling = shallow(autoscalingSection.find(GceAutoscalingPolicyDetails).getElement());
    const autoHealing = shallow(autoHealingSection.find(GceAutoHealingPolicyDetails).getElement());

    expect(autoscaling.text()).toContain('Min # VMs');
    expect(autoscaling.find('[data-testid="edit-autoscaling-policy"]').length).toBe(0);
    expect(autoscaling.find('[data-testid="delete-autoscaling-policy"]').length).toBe(0);
    expect(autoHealing.text()).toContain('fnord-health-check');
    expect(autoHealing.find('[data-testid="edit-auto-healing-policy"]').length).toBe(0);
    expect(autoHealing.find('[data-testid="delete-auto-healing-policy"]').length).toBe(0);
  });

  it('does not offer policy creation when GCE ad-hoc infrastructure writes are disabled', () => {
    spyOn(CloudProviderRegistry, 'isDisabled').and.returnValue(true);
    const serverGroupWithoutPolicies = {
      ...serverGroup,
      autoscalingPolicy: undefined,
      autoHealingPolicy: undefined,
    };
    const renderedSections = gceServerGroupDetailsSections.map((Section) =>
      shallow(<Section app={app} serverGroup={serverGroupWithoutPolicies} />),
    );
    const autoscalingSection = renderedSections.find(
      (section) => section.find(CollapsibleSection).prop('heading') === 'Autoscaling',
    );
    const autoHealingSection = renderedSections.find(
      (section) => section.find(CollapsibleSection).prop('heading') === 'Auto-healing',
    );
    const autoscaling = shallow(autoscalingSection.find(GceAutoscalingPolicyDetails).getElement());
    const autoHealing = shallow(autoHealingSection.find(GceAutoHealingPolicyDetails).getElement());

    expect(autoscaling.find('[data-testid="add-autoscaling-policy"]').length).toBe(0);
    expect(autoHealing.find('[data-testid="add-auto-healing-policy"]').length).toBe(0);
  });

  it('adds rollback and resize without changing existing enabled action visibility', () => {
    const wrapper = mount(<GceServerGroupActions app={app} serverGroup={serverGroup} />);

    expect(managedAction(wrapper, 'Rollback').length).toBe(1);
    expect(managedAction(wrapper, 'Resize').length).toBe(1);
    expect(linkAction(wrapper, 'Clone').length).toBe(1);
    expect(linkAction(wrapper, 'Disable').length).toBe(1);
    expect(linkAction(wrapper, 'Enable').length).toBe(0);
    expect(linkAction(wrapper, 'Destroy').length).toBe(1);
  });

  it('keeps rollback hidden for a disabled server group without changing existing disabled action visibility', () => {
    const wrapper = mount(<GceServerGroupActions app={app} serverGroup={{ ...serverGroup, isDisabled: true }} />);

    expect(managedAction(wrapper, 'Rollback').length).toBe(0);
    expect(managedAction(wrapper, 'Resize').length).toBe(1);
    expect(linkAction(wrapper, 'Disable').length).toBe(0);
    expect(linkAction(wrapper, 'Enable').length).toBe(1);
  });

  it('opens rollback with filtered candidates and the existing server group writer', () => {
    const show = spyOn(GceRollbackServerGroupModal, 'show').and.returnValue(Promise.resolve({} as any));
    const wrapper = mount(<GceServerGroupActions app={app} serverGroup={serverGroup} />);

    managedAction(wrapper, 'Rollback').prop('onClick')();

    expect(show).toHaveBeenCalledOnceWith({
      application: app,
      serverGroup,
      serverGroups: [eligibleRollbackCandidate],
      serverGroupWriter: runtimeServices.serverGroupWriter,
    });
  });

  it('keeps rollback available when there are no candidates and delegates empty handling to the modal', () => {
    const appWithoutCandidates = { ...app, serverGroups: { ...app.serverGroups, data: [] } };
    const show = spyOn(GceRollbackServerGroupModal, 'show').and.returnValue(Promise.resolve({} as any));
    const wrapper = mount(<GceServerGroupActions app={appWithoutCandidates} serverGroup={serverGroup} />);

    expect(managedAction(wrapper, 'Rollback').length).toBe(1);
    managedAction(wrapper, 'Rollback').prop('onClick')();

    expect(show).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({ application: appWithoutCandidates, serverGroup, serverGroups: [] }),
    );
  });

  it('opens resize with the completed writers', () => {
    const show = spyOn(GceResizeServerGroupModal, 'show').and.returnValue(Promise.resolve());
    const wrapper = mount(<GceServerGroupActions app={app} serverGroup={serverGroup} />);

    managedAction(wrapper, 'Resize').prop('onClick')();

    expect(show).toHaveBeenCalledOnceWith({
      application: app,
      autoscalingPolicyWriter: GceAutoscalingPolicyWriter,
      serverGroup,
      serverGroupWriter: runtimeServices.serverGroupWriter,
    });
  });

  it('protects rollback and resize with the managed-resource interstitial', () => {
    const wrapper = mount(<GceServerGroupActions app={app} serverGroup={{ ...serverGroup, isManaged: true }} />);

    ['Rollback', 'Resize'].forEach((label) => {
      expect(managedAction(wrapper, label).props()).toEqual(
        jasmine.objectContaining({ application: app, resource: jasmine.objectContaining({ isManaged: true }) }),
      );
    });
  });

  it('hides server group actions when GCE ad-hoc infrastructure writes are disabled', () => {
    spyOn(CloudProviderRegistry, 'isDisabled').and.returnValue(true);

    const wrapper = mount(<GceServerGroupActions app={app} serverGroup={serverGroup} />);

    expect(wrapper.isEmptyRender()).toBe(true);
  });
});
