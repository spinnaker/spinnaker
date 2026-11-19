import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';

import { CloudProviderRegistry, ConfirmationModalService, DeckRuntimeContext } from '@spinnaker/core';

import { GceAutoscalingPolicyWriter } from '../../autoscalingPolicy';
import { GceCloneServerGroupModal } from '../configure/wizard/GceCloneServerGroupModal';
import { registerGoogleProvider } from '../../gce.module';
import {
  GceAutoHealingPolicySection,
  GceAutoscalingPolicySection,
  GceServerGroupActions,
  GceServerGroupCustomMetadataSection,
  gceServerGroupDetailsSections,
  GceServerGroupLabelsSection,
  GceServerGroupLaunchConfigSection,
  GceServerGroupTagsSection,
} from './gceServerGroupDetails';
import { GceServerGroupDiskDescriptions } from './GceServerGroupDiskDescriptions';
import { GceResizeServerGroupModal } from './resize/GceResizeServerGroupModal';
import { GceRollbackServerGroupModal } from './rollback/GceRollbackServerGroupModal';

describe('GCE server group details integration', () => {
  let runtimeServices: any;
  const RuntimeWrapper = ({ children }: React.PropsWithChildren<{}>) => (
    <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>{children}</DeckRuntimeContext.Provider>
  );
  const renderWithRuntime = (component: React.ReactElement) => render(<RuntimeWrapper>{component}</RuntimeWrapper>);

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
      refresh: vi.fn(),
    },
  } as any;

  it('registered policy sections derive enabled mutation controls from the provider state', () => {
    registerGoogleProvider();
    vi.spyOn(CloudProviderRegistry, 'isDisabled').mockReturnValue(false);

    const sections = CloudProviderRegistry.getValue('gce', 'serverGroup.detailsSections');
    expect(sections).toEqual(gceServerGroupDetailsSections);
    const policySections = policySectionsFrom(sections);
    render(
      <>
        {policySections.map((Section: React.ComponentType<any>, index: number) => (
          <Section key={index} app={app} serverGroup={serverGroup} />
        ))}
      </>,
    );

    expandSection('Autoscaling');
    expandSection('Auto-healing');
    expect(screen.getByText('Min # VMs')).toBeInTheDocument();
    expect(screen.getByText('fnord-health-check')).toBeInTheDocument();
    expect(screen.getByTestId('edit-autoscaling-policy')).toBeInTheDocument();
    expect(screen.getByTestId('delete-autoscaling-policy')).toBeInTheDocument();
    expect(screen.getByTestId('edit-auto-healing-policy')).toBeInTheDocument();
    expect(screen.getByTestId('delete-auto-healing-policy')).toBeInTheDocument();
  });

  it('registered policy sections derive read-only summaries from disabled provider state', () => {
    registerGoogleProvider();
    vi.spyOn(CloudProviderRegistry, 'isDisabled').mockReturnValue(true);
    const policySections = policySectionsFrom(CloudProviderRegistry.getValue('gce', 'serverGroup.detailsSections'));
    render(
      <>
        {policySections.map((Section: React.ComponentType<any>, index: number) => (
          <Section key={index} app={app} serverGroup={serverGroup} />
        ))}
      </>,
    );
    expandSection('Autoscaling');
    expandSection('Auto-healing');

    expect(screen.getByText('Min # VMs')).toBeInTheDocument();
    expect(screen.getByText('fnord-health-check')).toBeInTheDocument();
    expect(screen.queryByTestId('edit-autoscaling-policy')).not.toBeInTheDocument();
    expect(screen.queryByTestId('delete-autoscaling-policy')).not.toBeInTheDocument();
    expect(screen.queryByTestId('edit-auto-healing-policy')).not.toBeInTheDocument();
    expect(screen.queryByTestId('delete-auto-healing-policy')).not.toBeInTheDocument();
  });

  it('registered policy sections do not offer policy creation when provider mutations are disabled', () => {
    registerGoogleProvider();
    vi.spyOn(CloudProviderRegistry, 'isDisabled').mockReturnValue(true);
    const serverGroupWithoutPolicies = {
      ...serverGroup,
      autoscalingPolicy: undefined,
      autoHealingPolicy: undefined,
    };
    const policySections = policySectionsFrom(CloudProviderRegistry.getValue('gce', 'serverGroup.detailsSections'));
    render(
      <>
        {policySections.map((Section: React.ComponentType<any>, index: number) => (
          <Section key={index} app={app} serverGroup={serverGroupWithoutPolicies} />
        ))}
      </>,
    );

    expandSection('Autoscaling');
    expandSection('Auto-healing');
    expect(screen.queryByTestId('add-autoscaling-policy')).not.toBeInTheDocument();
    expect(screen.queryByTestId('add-auto-healing-policy')).not.toBeInTheDocument();
  });

  it('renders target shape and instance flexibility in the launch configuration without nesting definition lists', () => {
    const instanceFlexibilityPolicy = {
      instanceSelections: {
        preferred: { rank: 1, machineTypes: ['n2-standard-8'] },
      },
    };
    const { container } = render(
      <GceServerGroupLaunchConfigSection
        app={app}
        serverGroup={{
          ...serverGroup,
          distributionPolicy: { targetShape: 'BALANCED' },
          instanceFlexibilityPolicy,
        }}
      />,
    );

    expect(screen.getAllByText('Target shape', { selector: 'dt' })).toHaveLength(1);
    expect(screen.getAllByText('BALANCED', { selector: 'dd' })).toHaveLength(1);
    expect(definition('Target shape')).toBe('BALANCED');
    expect(definition('Selection')).toBe('preferred');
    expect(definition('Rank')).toBe('1');
    expect(definition('Machine types')).toBe('n2-standard-8');
    expect(container.querySelectorAll('dl dl')).toHaveLength(0);
  });

  it('renders shielded settings with stable, legacy, then top-level field precedence', () => {
    render(
      <GceServerGroupLaunchConfigSection
        app={app}
        serverGroup={{
          ...serverGroup,
          shieldedInstanceConfig: { enableSecureBoot: false },
          shieldedVmConfig: { enableSecureBoot: true, enableVtpm: false },
          enableVtpm: true,
          enableIntegrityMonitoring: false,
        }}
      />,
    );

    expect(definition('Secure Boot')).toBe('false');
    expect(definition('vTPM')).toBe('false');
    expect(definition('Integrity Monitoring')).toBe('false');
  });

  it('restores image, instance type, and the legacy launch configuration fields', () => {
    render(
      <GceServerGroupLaunchConfigSection
        app={app}
        serverGroup={{
          ...serverGroup,
          network: 'default',
          subnet: 'default-subnet',
          launchConfig: {
            imageId: 'fnord-image-v1',
            instanceType: 'custom-2-4096',
            launchConfigurationName: 'fnord-main-v004-template',
            minCpuPlatform: 'Intel Skylake',
            instanceTemplate: {
              name: 'fnord-main-v004-template',
              properties: {
                canIpForward: true,
                disks: [
                  {
                    boot: true,
                    deviceName: 'boot',
                    initializeParams: {
                      diskSizeGb: 10,
                      diskType: 'pd-ssd',
                      sourceImage: 'https://compute.googleapis.com/projects/p/global/images/fnord-image-v1',
                    },
                  },
                ],
                metadata: { items: [{ key: 'startup-script', value: 'echo hello' }] },
                networkInterfaces: [{ accessConfigs: [{ name: 'External NAT' }] }],
                serviceAccounts: [
                  {
                    email: 'fnord@test.iam.gserviceaccount.com',
                    scopes: ['https://www.googleapis.com/auth/cloud-platform'],
                  },
                ],
              },
            },
          },
        }}
      />,
    );

    expect(definition('Image')).toBe('fnord-image-v1');
    expect(definition('Instance Type')).toBe('N1 2 vCPU / 4 GB RAM');
    expect(definition('Minimum CPU Platform')).toBe('Intel Skylake');
    expect(definition('Boot Disk')).toBe('Persistent SSD: 10GB');
    expect(definition('Service Account')).toBe('fnord@test.iam.gserviceaccount.com');
    expect(definition('Auth Scopes')?.trim()).toBe('cloud-platform');
    expect(definition('Network')).toBe('default');
    expect(definition('Subnet')).toBe('default-subnet');
    expect(definition('Associate Public IP Address')).toBe('true');
    expect(definition('Can IP Forward')).toBe('true');
    expect(screen.queryByText('Name', { selector: 'dt' })).not.toBeInTheDocument();

    expect(screen.queryByText('echo hello')).not.toBeInTheDocument();
    fireEvent.click(screen.getByText('Show Startup Script'));
    expect(screen.getByText('echo hello', { selector: 'pre' })).toBeInTheDocument();
    expect(screen.getByText('Hide Startup Script')).toBeInTheDocument();
  });

  it('falls back to defaults when launch configuration fields are absent', () => {
    render(<GceServerGroupLaunchConfigSection app={app} serverGroup={serverGroup} />);

    expect(definition('Image')).toBe('-');
    expect(definition('Instance Type')).toBe('-');
    expect(definition('Minimum CPU Platform')).toBe('(Automatic)');
    expect(definition('Associate Public IP Address')).toBe('false');
    expect(definition('Startup Script')).toBe('[none]');
    expect(screen.queryByText('Service Account', { selector: 'dt' })).not.toBeInTheDocument();
    expect(screen.queryByText('Auth Scopes', { selector: 'dt' })).not.toBeInTheDocument();
  });

  it('renders boot and additional disk descriptions from the instance template', () => {
    const { container } = render(
      <dl>
        <GceServerGroupDiskDescriptions
          application={app}
          serverGroup={
            {
              ...serverGroup,
              launchConfig: {
                instanceTemplate: {
                  properties: {
                    disks: [
                      {
                        boot: true,
                        deviceName: 'boot',
                        initializeParams: {
                          diskSizeGb: 10,
                          diskType: 'pd-ssd',
                          sourceImage: 'https://compute.googleapis.com/projects/p/global/images/fnord-image-v1',
                        },
                      },
                      { boot: false, deviceName: 'data', initializeParams: { diskSizeGb: 375, diskType: 'local-ssd' } },
                    ],
                  },
                },
              },
            } as any
          }
        />
      </dl>,
    );

    expect(Array.from(container.querySelectorAll('dt')).map((term) => term.textContent)).toEqual(['Boot Disk', 'Disk']);
    expect(Array.from(container.querySelectorAll('dd')).map((definition) => definition.textContent)).toEqual([
      'Persistent SSD: 10GB',
      'Image: fnord-image-v1',
      'Local SSD: 375GB',
    ]);
  });

  it('registers custom metadata, tags, and labels sections', () => {
    registerGoogleProvider();

    expect(CloudProviderRegistry.getValue('gce', 'serverGroup.detailsSections')).toEqual(
      expect.arrayContaining([
        GceServerGroupCustomMetadataSection,
        GceServerGroupTagsSection,
        GceServerGroupLabelsSection,
      ]),
    );
  });

  it('renders truncated custom metadata from the instance template', () => {
    const { container } = render(
      <GceServerGroupCustomMetadataSection
        app={app}
        serverGroup={{
          ...serverGroup,
          launchConfig: {
            instanceTemplate: {
              properties: {
                metadata: {
                  items: [
                    { key: 'load-balancer-names', value: 'fnord-lb' },
                    { key: 'long-value', value: 'x'.repeat(100) },
                  ],
                },
              },
            },
          },
        }}
      />,
    );
    expandSection('Custom Metadata');

    expect(Array.from(container.querySelectorAll('dt')).map((term) => term.textContent?.trim())).toEqual([
      'load-balancer-names',
      'long-value',
    ]);
    expect(definition('load-balancer-names')).toBe('fnord-lb');
    expect(definition('long-value')).toBe(`${'x'.repeat(90)}...`);
  });

  it('renders network tags with firewall help and instance template labels', () => {
    const appWithFirewalls = {
      ...app,
      securityGroups: {
        data: [
          { accountName: 'prod', name: 'allow-http', targetTags: ['http-server'] },
          { accountName: 'other', name: 'other-account-http', targetTags: ['http-server'] },
        ],
      },
    };
    const serverGroupWithTagsAndLabels = {
      ...serverGroup,
      instanceTemplateLabels: { team: 'fnord' },
      launchConfig: { instanceTemplate: { properties: { tags: { items: ['http-server', 'untagged'] } } } },
    };
    const { container } = render(
      <>
        <GceServerGroupTagsSection app={appWithFirewalls} serverGroup={serverGroupWithTagsAndLabels} />
        <GceServerGroupLabelsSection app={app} serverGroup={serverGroupWithTagsAndLabels} />
      </>,
    );
    expandSection('Tags');
    expandSection('Labels');

    expect(Array.from(container.querySelectorAll('dd')).map((tag) => tag.textContent?.trim())).toEqual([
      'http-server',
      'untagged',
      'fnord',
    ]);
    expect(container.querySelectorAll('dd .help-field')).toHaveLength(1);
    expect(definition('team')).toBe('fnord');
  });

  it('shows empty-state messages when metadata, tags, and labels are missing', () => {
    render(
      <>
        <GceServerGroupCustomMetadataSection app={app} serverGroup={serverGroup} />
        <GceServerGroupTagsSection app={app} serverGroup={serverGroup} />
        <GceServerGroupLabelsSection app={app} serverGroup={serverGroup} />
      </>,
    );
    ['Custom Metadata', 'Tags', 'Labels'].forEach(expandSection);

    expect(screen.getByText('No custom metadata associated with this server group')).toBeInTheDocument();
    expect(screen.getByText('No tags associated with this server group')).toBeInTheDocument();
    expect(screen.getByText('No labels associated with this server group')).toBeInTheDocument();
  });

  it('adds rollback and resize without changing existing enabled action visibility', () => {
    registerGoogleProvider();
    const RegisteredActions = CloudProviderRegistry.getValue('gce', 'serverGroup.detailsActions');
    renderWithRuntime(<RegisteredActions app={app} serverGroup={serverGroup} />);

    expect(screen.getByText('Rollback')).toBeInTheDocument();
    expect(screen.getByText('Resize')).toBeInTheDocument();
    expect(screen.getByText('Clone')).toBeInTheDocument();
    expect(screen.getByText('Disable')).toBeInTheDocument();
    expect(screen.queryByText('Enable')).not.toBeInTheDocument();
    expect(screen.getByText('Destroy')).toBeInTheDocument();
  });

  it('opens clone with the command builder and runtime services from Deck context', async () => {
    const command = { application: serverGroup.app, stack: 'main' };
    runtimeServices.serverGroupCommandBuilder = {
      buildServerGroupCommandFromExisting: vi.fn().mockResolvedValue(command),
    };
    const show = vi.spyOn(GceCloneServerGroupModal, 'show').mockResolvedValue({} as any);
    renderWithRuntime(<GceServerGroupActions app={app} serverGroup={serverGroup} />);

    fireEvent.click(screen.getByText('Clone'));

    expect(runtimeServices.serverGroupCommandBuilder.buildServerGroupCommandFromExisting).toHaveBeenCalledWith(
      app,
      serverGroup,
    );
    await waitFor(() =>
      expect(show).toHaveBeenCalledWith(
        { application: app, command, title: `Clone ${serverGroup.name}` },
        runtimeServices,
      ),
    );
  });

  it('keeps rollback hidden for a disabled server group without changing existing disabled action visibility', () => {
    renderWithRuntime(<GceServerGroupActions app={app} serverGroup={{ ...serverGroup, isDisabled: true }} />);

    expect(screen.queryByText('Rollback')).not.toBeInTheDocument();
    expect(screen.getByText('Resize')).toBeInTheDocument();
    expect(screen.queryByText('Disable')).not.toBeInTheDocument();
    expect(screen.getByText('Enable')).toBeInTheDocument();
  });

  it('opens rollback with filtered candidates and the existing server group writer', async () => {
    const show = vi.spyOn(GceRollbackServerGroupModal, 'show').mockReturnValue(Promise.resolve({} as any));
    renderWithRuntime(<GceServerGroupActions app={app} serverGroup={serverGroup} />);

    fireEvent.click(screen.getByText('Rollback'));

    await waitFor(() =>
      expect(show).toHaveBeenCalledExactlyOnceWith({
        application: app,
        serverGroup,
        serverGroups: [eligibleRollbackCandidate],
        serverGroupWriter: runtimeServices.serverGroupWriter,
      }),
    );
  });

  it('keeps rollback available when there are no candidates and delegates empty handling to the modal', async () => {
    const appWithoutCandidates = { ...app, serverGroups: { ...app.serverGroups, data: [] } };
    const show = vi.spyOn(GceRollbackServerGroupModal, 'show').mockReturnValue(Promise.resolve({} as any));
    renderWithRuntime(<GceServerGroupActions app={appWithoutCandidates} serverGroup={serverGroup} />);

    fireEvent.click(screen.getByText('Rollback'));

    await waitFor(() =>
      expect(show).toHaveBeenCalledExactlyOnceWith(
        expect.objectContaining({ application: appWithoutCandidates, serverGroup, serverGroups: [] }),
      ),
    );
  });

  it('opens resize with the completed writers', async () => {
    const show = vi.spyOn(GceResizeServerGroupModal, 'show').mockReturnValue(Promise.resolve());
    renderWithRuntime(<GceServerGroupActions app={app} serverGroup={serverGroup} />);

    fireEvent.click(screen.getByText('Resize'));

    await waitFor(() =>
      expect(show).toHaveBeenCalledExactlyOnceWith({
        application: app,
        autoscalingPolicyWriter: GceAutoscalingPolicyWriter,
        serverGroup,
        serverGroupWriter: runtimeServices.serverGroupWriter,
      }),
    );
  });

  it('protects rollback and resize with the managed-resource interstitial', () => {
    const confirm = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(new Promise(() => undefined) as any);
    renderWithRuntime(
      <GceServerGroupActions
        app={app}
        serverGroup={{
          ...serverGroup,
          isManaged: true,
          managedResourceSummary: {
            id: 'managed-resource-id',
            isPaused: false,
            locations: { account: 'prod', regions: [] },
          },
        }}
      />,
    );

    ['Rollback', 'Resize'].forEach((label) => {
      fireEvent.click(screen.getByText(label));
    });
    expect(confirm).toHaveBeenCalledTimes(2);
    expect(confirm.mock.calls.every(([params]) => params.header === 'Pause Management?')).toBe(true);
  });

  it('hides server group actions when GCE ad-hoc infrastructure writes are disabled', () => {
    registerGoogleProvider();
    vi.spyOn(CloudProviderRegistry, 'isDisabled').mockReturnValue(true);
    const RegisteredActions = CloudProviderRegistry.getValue('gce', 'serverGroup.detailsActions');

    const { container } = renderWithRuntime(<RegisteredActions app={app} serverGroup={serverGroup} />);

    expect(container).toBeEmptyDOMElement();
  });
});

function expandSection(name: string): void {
  const heading = screen.getByRole('heading', { name });
  const section = heading.closest('.collapsible-section');
  if (!section?.querySelector('.content-body')) {
    fireEvent.click(heading.parentElement as HTMLElement);
  }
}

function definition(term: string): string | undefined {
  return screen.getByText(term, { selector: 'dt' }).nextElementSibling?.textContent ?? undefined;
}

function policySectionsFrom(sections: Array<React.ComponentType<any>>): Array<React.ComponentType<any>> {
  return sections.filter(
    (section) => section === GceAutoscalingPolicySection || section === GceAutoHealingPolicySection,
  );
}
