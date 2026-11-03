import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { hashLocationPlugin, servicesPlugin, UIRouterContext, UIRouterReact, UIViewContext } from '@uirouter/react';
import React from 'react';

import type { Application, ISecurityGroup } from '@spinnaker/core';
import { CollapsibleSectionStateCache, DeckRuntimeContext, FirewallLabels } from '@spinnaker/core';

import type { IAmazonServerGroupView } from '../../../domain';
import { AWSProviderSettings } from '../../../aws.settings';
import { EditAsgAdvancedSettingsModal } from '../advancedSettings';
import { ModifyScalingProcessesModal } from '../scalingProcesses';
import { EditScheduledActionsModal } from '../scheduledActions';
import { EditSecurityGroupsModal } from '../securityGroups';
import { AdvancedSettingsDetailsSection } from './AdvancedSettingsDetailsSection';
import { ScalingProcessesDetailsSection } from './ScalingProcessesDetailsSection';
import { ScheduledActionsDetailsSection } from './ScheduledActionsDetailsSection';
import { SecurityGroupsDetailsSection } from './SecurityGroupsDetailsSection';

describe('Amazon server group maintenance action integration', () => {
  let router: UIRouterReact;
  const originalAdHocInfraWritesEnabled = AWSProviderSettings.adHocInfraWritesEnabled;
  const runtimeServices = {} as any;
  const editSecurityGroupsLabel = `Edit ${FirewallLabels.get('Firewalls')}`;
  const resolvedSecurityGroup = {
    accountName: 'test-account',
    id: 'sg-123',
    name: 'application-security-group',
    region: 'us-east-1',
    vpcId: 'vpc-123',
  } as ISecurityGroup;
  const application = ({
    name: 'test-app',
    securityGroups: { data: [resolvedSecurityGroup] },
    serverGroups: { refresh: vi.fn() },
  } as any) as Application;
  const serverGroup = {
    account: 'test-account',
    asg: {
      defaultCooldown: 300,
      enabledMetrics: [],
      healthCheckGracePeriod: 0,
      healthCheckType: 'EC2',
      suspendedProcesses: [],
      terminationPolicies: ['Default'],
    },
    name: 'test-app-main-v001',
    region: 'us-east-1',
    scalingPolicies: [],
    scheduledActions: [],
    securityGroups: ['sg-123'],
    type: 'aws',
    vpcId: 'vpc-123',
  } as IAmazonServerGroupView;

  const renderSection = (section: React.ReactElement) =>
    render(
      <UIRouterContext.Provider value={router}>
        <UIViewContext.Provider
          value={{
            fqn: 'application.serverGroup',
            context: router.stateRegistry.get('application.serverGroup') as any,
          }}
        >
          <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>
            {section}
          </DeckRuntimeContext.Provider>
        </UIViewContext.Provider>
      </UIRouterContext.Provider>,
    );
  const expand = (heading: string) => fireEvent.click(screen.getByText(heading));

  beforeEach(() => {
    vi.spyOn(CollapsibleSectionStateCache, 'isSet').mockReturnValue(false);
    AWSProviderSettings.adHocInfraWritesEnabled = true;
    router = new UIRouterReact();
    router.plugin(servicesPlugin);
    router.plugin(hashLocationPlugin);
    ['application', 'application.serverGroup', 'application.firewallDetails'].forEach((name) =>
      router.stateRegistry.register({ name, url: `/${name.split('.').pop()}` }),
    );
  });

  afterEach(() => {
    router.dispose();
    AWSProviderSettings.adHocInfraWritesEnabled = originalAdHocInfraWritesEnabled;
  });

  it('opens Advanced Settings with the exact application and enriched server group', async () => {
    const show = vi.spyOn(EditAsgAdvancedSettingsModal, 'show').mockReturnValue(undefined);
    renderSection(<AdvancedSettingsDetailsSection app={application} serverGroup={serverGroup} />);

    expand('Advanced Settings');
    await userEvent.click(screen.getByText('Edit Advanced Settings'));

    expect(show).toHaveBeenCalledExactlyOnceWith({ application, serverGroup }, runtimeServices);
  });

  it('opens Scaling Processes with the exact application and enriched server group', async () => {
    const show = vi.spyOn(ModifyScalingProcessesModal, 'show').mockReturnValue(undefined);
    renderSection(<ScalingProcessesDetailsSection app={application} serverGroup={serverGroup} />);

    expand('Scaling Processes');
    await userEvent.click(screen.getByText('Edit Scaling Processes'));

    expect(show).toHaveBeenCalledExactlyOnceWith({ application, serverGroup });
  });

  it('opens Scheduled Actions with the exact application and enriched server group', async () => {
    const show = vi.spyOn(EditScheduledActionsModal, 'show').mockReturnValue(undefined);
    renderSection(<ScheduledActionsDetailsSection app={application} serverGroup={serverGroup} />);

    expand('Scheduled Actions');
    await userEvent.click(screen.getByText('Edit Scheduled Actions'));

    expect(show).toHaveBeenCalledExactlyOnceWith({ application, serverGroup });
  });

  it('opens Security Groups with resolved groups and exact application and enriched server group', async () => {
    const show = vi.spyOn(EditSecurityGroupsModal, 'show').mockReturnValue(undefined);
    renderSection(<SecurityGroupsDetailsSection app={application} serverGroup={serverGroup} />);

    expand(FirewallLabels.get('Firewalls'));
    await userEvent.click(screen.getByText(editSecurityGroupsLabel));

    expect(show).toHaveBeenCalledExactlyOnceWith(
      { application, securityGroups: [resolvedSecurityGroup], serverGroup },
      runtimeServices,
    );
  });

  it('hides all maintenance links when ad-hoc infrastructure writes are disabled', () => {
    AWSProviderSettings.adHocInfraWritesEnabled = false;

    renderSection(
      <>
        <AdvancedSettingsDetailsSection app={application} serverGroup={serverGroup} />
        <ScalingProcessesDetailsSection app={application} serverGroup={serverGroup} />
        <ScheduledActionsDetailsSection app={application} serverGroup={serverGroup} />
        <SecurityGroupsDetailsSection app={application} serverGroup={serverGroup} />
      </>,
    );

    ['Advanced Settings', 'Scaling Processes', 'Scheduled Actions', FirewallLabels.get('Firewalls')].forEach(expand);
    expect(screen.queryByText('Edit Advanced Settings')).not.toBeInTheDocument();
    expect(screen.queryByText('Edit Scaling Processes')).not.toBeInTheDocument();
    expect(screen.queryByText('Edit Scheduled Actions')).not.toBeInTheDocument();
    expect(screen.queryByText(editSecurityGroupsLabel)).not.toBeInTheDocument();
  });

  it('hides Security Groups editing when the server group has no VPC', () => {
    renderSection(
      <SecurityGroupsDetailsSection app={application} serverGroup={{ ...serverGroup, vpcId: undefined }} />,
    );

    expand(FirewallLabels.get('Firewalls'));
    expect(screen.queryByText(editSecurityGroupsLabel)).not.toBeInTheDocument();
  });
});
