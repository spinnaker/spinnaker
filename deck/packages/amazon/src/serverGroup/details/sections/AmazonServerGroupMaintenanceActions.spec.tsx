import { shallow } from 'enzyme';
import React from 'react';

import type { Application, ISecurityGroup } from '@spinnaker/core';
import { FirewallLabels } from '@spinnaker/core';

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
    serverGroups: { refresh: jasmine.createSpy('refresh') },
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

  const editLink = (wrapper: ReturnType<typeof shallow>, label: string) =>
    wrapper.find('a.clickable').filterWhere((link) => link.text() === label);

  const withRuntimeServices = <T extends React.Component>(wrapper: ReturnType<typeof shallow>) => {
    (wrapper.instance() as T).context = { services: runtimeServices };
    return wrapper;
  };

  beforeEach(() => {
    AWSProviderSettings.adHocInfraWritesEnabled = true;
  });

  afterEach(() => {
    AWSProviderSettings.adHocInfraWritesEnabled = originalAdHocInfraWritesEnabled;
  });

  it('opens Advanced Settings with the exact application and enriched server group', () => {
    const show = spyOn(EditAsgAdvancedSettingsModal, 'show');
    const wrapper = withRuntimeServices(
      shallow(<AdvancedSettingsDetailsSection app={application} serverGroup={serverGroup} />),
    );

    editLink(wrapper, 'Edit Advanced Settings').simulate('click');

    expect(show).toHaveBeenCalledOnceWith({ application, serverGroup }, runtimeServices);
  });

  it('opens Scaling Processes with the exact application and enriched server group', () => {
    const show = spyOn(ModifyScalingProcessesModal, 'show');
    const wrapper = shallow(<ScalingProcessesDetailsSection app={application} serverGroup={serverGroup} />);

    editLink(wrapper, 'Edit Scaling Processes').simulate('click');

    expect(show).toHaveBeenCalledOnceWith({ application, serverGroup });
  });

  it('opens Scheduled Actions with the exact application and enriched server group', () => {
    const show = spyOn(EditScheduledActionsModal, 'show');
    const wrapper = shallow(<ScheduledActionsDetailsSection app={application} serverGroup={serverGroup} />);

    editLink(wrapper, 'Edit Scheduled Actions').simulate('click');

    expect(show).toHaveBeenCalledOnceWith({ application, serverGroup });
  });

  it('opens Security Groups with resolved groups and exact application and enriched server group', () => {
    const show = spyOn(EditSecurityGroupsModal, 'show');
    const wrapper = withRuntimeServices(
      shallow(<SecurityGroupsDetailsSection app={application} serverGroup={serverGroup} />),
    );

    editLink(wrapper, editSecurityGroupsLabel).simulate('click');

    expect(show).toHaveBeenCalledOnceWith(
      { application, securityGroups: [resolvedSecurityGroup], serverGroup },
      runtimeServices,
    );
  });

  it('hides all maintenance links when ad-hoc infrastructure writes are disabled', () => {
    AWSProviderSettings.adHocInfraWritesEnabled = false;

    expect(
      editLink(
        shallow(<AdvancedSettingsDetailsSection app={application} serverGroup={serverGroup} />),
        'Edit Advanced Settings',
      ).length,
    ).toBe(0);
    expect(
      editLink(
        shallow(<ScalingProcessesDetailsSection app={application} serverGroup={serverGroup} />),
        'Edit Scaling Processes',
      ).length,
    ).toBe(0);
    expect(
      editLink(
        shallow(<ScheduledActionsDetailsSection app={application} serverGroup={serverGroup} />),
        'Edit Scheduled Actions',
      ).length,
    ).toBe(0);
    expect(
      editLink(
        shallow(<SecurityGroupsDetailsSection app={application} serverGroup={serverGroup} />),
        editSecurityGroupsLabel,
      ).length,
    ).toBe(0);
  });

  it('hides Security Groups editing when the server group has no VPC', () => {
    const wrapper = shallow(
      <SecurityGroupsDetailsSection app={application} serverGroup={{ ...serverGroup, vpcId: undefined }} />,
    );

    expect(editLink(wrapper, editSecurityGroupsLabel).length).toBe(0);
  });
});
