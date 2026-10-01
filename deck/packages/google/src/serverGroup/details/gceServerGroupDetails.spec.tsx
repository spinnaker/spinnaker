import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';

import { CloudProviderRegistry, ConfirmationModalService, DeckRuntimeContext } from '@spinnaker/core';

import { GceAutoscalingPolicyWriter } from '../../autoscalingPolicy';
import { GceCloneServerGroupModal } from '../configure/wizard/GceCloneServerGroupModal';
import { registerGoogleProvider } from '../../gce.module';
import {
  GceServerGroupActions,
  gceServerGroupDetailsSections,
  GceServerGroupLaunchConfigSection,
} from './gceServerGroupDetails';
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
    const policySections = sections.slice(-2);
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
    const policySections = CloudProviderRegistry.getValue('gce', 'serverGroup.detailsSections').slice(-2);
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
    const policySections = CloudProviderRegistry.getValue('gce', 'serverGroup.detailsSections').slice(-2);
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
