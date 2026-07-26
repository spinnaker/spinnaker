import React from 'react';
import { Observable } from 'rxjs';

import {
  AccountTag,
  CloudProviderRegistry,
  CollapsibleSection,
  ConfirmationModalService,
  ErrorModalService,
  ManagedMenuItem,
  ServerGroupReader,
  ServerGroupWarningMessageService,
  useDeckRuntimeServices,
} from '@spinnaker/core';
import type { Application } from '@spinnaker/core';

import { GceAutoHealingPolicyDetails } from './autoHealingPolicy';
import { GceAutoscalingPolicyWriter } from '../../autoscalingPolicy';
import { GceAutoscalingPolicyDetails } from './autoscalingPolicy';
import { GceXpnNamingService } from '../../common/xpnNaming.gce.service';
import { GceCloneServerGroupModal } from '../configure/wizard/GceCloneServerGroupModal';
import type { IGceServerGroup } from '../../domain';
import { GceResizeServerGroupModal } from './resize/GceResizeServerGroupModal';
import { GceRollbackServerGroupModal, getGceRollbackCandidates } from './rollback/GceRollbackServerGroupModal';

function findServerGroupSummary(app: any, serverGroup: any): any {
  const serverGroups = app?.serverGroups?.data || [];
  const summary = serverGroups.find((candidate: any) => {
    return (
      candidate.name === serverGroup.name &&
      candidate.account === serverGroup.accountId &&
      candidate.region === serverGroup.region
    );
  });
  if (summary) {
    return summary;
  }

  const loadBalancers = app?.loadBalancers?.data || [];
  for (const loadBalancer of loadBalancers) {
    const match = (loadBalancer.serverGroups || []).find((candidate: any) => {
      return (
        candidate.name === serverGroup.name &&
        candidate.account === serverGroup.accountId &&
        candidate.region === serverGroup.region
      );
    });
    if (match) {
      return match;
    }
  }

  return null;
}

function projectFromTemplate(serverGroup: any): string | undefined {
  const instanceTemplate = serverGroup.launchConfig?.instanceTemplate;
  return instanceTemplate ? new GceXpnNamingService().deriveProjectId(instanceTemplate) : undefined;
}

function decorateServerGroup(serverGroup: any): any {
  const project = projectFromTemplate(serverGroup);
  const instanceTemplate = serverGroup.launchConfig?.instanceTemplate;
  const networkInterface = instanceTemplate?.properties?.networkInterfaces?.[0] || {};
  const xpnNaming = new GceXpnNamingService();
  return {
    ...serverGroup,
    account: serverGroup.account || serverGroup.accountId,
    logsLink: project
      ? `https://console.cloud.google.com/logs/query?project=${encodeURIComponent(project)}&resource=gce_instance_group`
      : undefined,
    network: xpnNaming.decorateXpnResourceIfNecessary(project, networkInterface.network),
    subnet: xpnNaming.decorateXpnResourceIfNecessary(project, networkInterface.subnetwork),
    zones: [...(serverGroup.zones || [])].sort(),
  };
}

export function gceServerGroupDetailsGetter(props: any, autoClose: () => void): Observable<any> {
  return new Observable((observer) => {
    const summary = findServerGroupSummary(props.app, props.serverGroup);

    ServerGroupReader.getServerGroup(
      props.app.name,
      props.serverGroup.accountId,
      props.serverGroup.region,
      props.serverGroup.name,
    ).then(
      (details: any) => {
        if (!details || Object.keys(details).length === 0) {
          autoClose();
          observer.complete();
          return;
        }
        observer.next(decorateServerGroup({ ...(summary || {}), ...details, account: props.serverGroup.accountId }));
        observer.complete();
      },
      () => {
        autoClose();
        observer.complete();
      },
    );
  });
}

function withGcePlatformHealthParams(app: any, params: any = {}): any {
  if (app.attributes?.platformHealthOnlyShowOverride && app.attributes?.platformHealthOnly) {
    return { ...params, interestingHealthProviderNames: params.interestingHealthProviderNames || ['Google'] };
  }

  return params;
}

export function cloneGceServerGroup(
  app: any,
  serverGroup: any,
  commandBuilder: any,
  runtimeServices: ReturnType<typeof useDeckRuntimeServices>,
): PromiseLike<void> {
  return commandBuilder
    .buildServerGroupCommandFromExisting(app, serverGroup)
    .then((command: any) => {
      GceCloneServerGroupModal.show({ application: app, command, title: `Clone ${serverGroup.name}` }, runtimeServices);
    })
    .catch((error: any) => {
      ErrorModalService.error({
        header: `Error cloning ${serverGroup.name}`,
        body: error?.data?.message || error?.message || 'Unable to build the clone server group command.',
      });
    });
}

export function GceServerGroupActions({ app, serverGroup }: { app: any; serverGroup: any }): JSX.Element {
  const runtimeServices = useDeckRuntimeServices();
  const { serverGroupCommandBuilder, serverGroupWriter } = runtimeServices;
  if (CloudProviderRegistry.isDisabled('gce')) {
    return null;
  }

  const confirmationDefaults = {
    account: serverGroup.account,
    askForReason: true,
    platformHealthOnlyShowOverride: app.attributes?.platformHealthOnlyShowOverride,
    platformHealthType: 'Google',
  };

  const cloneServerGroup = () => {
    cloneGceServerGroup(app, serverGroup, serverGroupCommandBuilder, runtimeServices);
  };

  const rollbackServerGroup = () => {
    const serverGroups = getGceRollbackCandidates(app, serverGroup, app.serverGroups?.data || []);
    GceRollbackServerGroupModal.show({
      application: app,
      serverGroup,
      serverGroups,
      serverGroupWriter,
    });
  };

  const resizeServerGroup = () => {
    GceResizeServerGroupModal.show({
      application: app,
      autoscalingPolicyWriter: GceAutoscalingPolicyWriter,
      serverGroup,
      serverGroupWriter,
    });
  };

  const destroyServerGroup = () => {
    const confirmationModalParams: any = {
      ...confirmationDefaults,
      buttonText: `Destroy ${serverGroup.name}`,
      header: `Really destroy ${serverGroup.name}?`,
      submitMethod: (params: any) =>
        serverGroupWriter.destroyServerGroup(serverGroup, app, withGcePlatformHealthParams(app, params)),
      taskMonitorConfig: { application: app, title: `Destroying ${serverGroup.name}` },
    };
    ServerGroupWarningMessageService.addDestroyWarningMessage(app, serverGroup, confirmationModalParams);
    ConfirmationModalService.confirm(confirmationModalParams);
  };

  const disableServerGroup = () => {
    const confirmationModalParams: any = {
      ...confirmationDefaults,
      buttonText: `Disable ${serverGroup.name}`,
      header: `Really disable ${serverGroup.name}?`,
      submitMethod: (params: any) =>
        serverGroupWriter.disableServerGroup(serverGroup, app.name, withGcePlatformHealthParams(app, params)),
      taskMonitorConfig: { application: app, title: `Disabling ${serverGroup.name}` },
    };
    ServerGroupWarningMessageService.addDisableWarningMessage(app, serverGroup, confirmationModalParams);
    ConfirmationModalService.confirm(confirmationModalParams);
  };

  const enableServerGroup = () => {
    ConfirmationModalService.confirm({
      ...confirmationDefaults,
      buttonText: `Enable ${serverGroup.name}`,
      header: `Really enable ${serverGroup.name}?`,
      submitMethod: (params: any) =>
        serverGroupWriter.enableServerGroup(serverGroup, app, withGcePlatformHealthParams(app, params)),
      taskMonitorConfig: { application: app, title: `Enabling ${serverGroup.name}` },
    });
  };

  return (
    <div className="dropdown" id="gce-server-group-actions-dropdown">
      <button className="btn btn-sm btn-primary dropdown-toggle" data-toggle="dropdown" type="button">
        Server Group Actions
      </button>
      <ul className="dropdown-menu">
        {!serverGroup.isDisabled && (
          <ManagedMenuItem resource={serverGroup} application={app} onClick={rollbackServerGroup}>
            Rollback
          </ManagedMenuItem>
        )}
        <ManagedMenuItem resource={serverGroup} application={app} onClick={resizeServerGroup}>
          Resize
        </ManagedMenuItem>
        <li>
          <a onClick={cloneServerGroup}>Clone</a>
        </li>
        {!serverGroup.isDisabled && (
          <li>
            <a onClick={disableServerGroup}>Disable</a>
          </li>
        )}
        {serverGroup.isDisabled && (
          <li>
            <a onClick={enableServerGroup}>Enable</a>
          </li>
        )}
        <li>
          <a onClick={destroyServerGroup}>Destroy</a>
        </li>
      </ul>
    </div>
  );
}

export function GceServerGroupInformationSection({ serverGroup }: { app: any; serverGroup: any }): JSX.Element {
  return (
    <CollapsibleSection heading="Server Group Information" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Account</dt>
        <dd>
          <AccountTag account={serverGroup.account} />
        </dd>
        <dt>Region</dt>
        <dd>{serverGroup.region}</dd>
        <dt>Network</dt>
        <dd>{serverGroup.network || '-'}</dd>
        <dt>Subnet</dt>
        <dd>{serverGroup.subnet || '-'}</dd>
      </dl>
    </CollapsibleSection>
  );
}

export function GceServerGroupCapacitySection({ serverGroup }: { app: any; serverGroup: any }): JSX.Element {
  return (
    <CollapsibleSection heading="Capacity" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Desired</dt>
        <dd>{serverGroup.capacity?.desired ?? serverGroup.instances?.length ?? '-'}</dd>
        <dt>Min</dt>
        <dd>{serverGroup.capacity?.min ?? '-'}</dd>
        <dt>Max</dt>
        <dd>{serverGroup.capacity?.max ?? '-'}</dd>
      </dl>
    </CollapsibleSection>
  );
}

export function GceServerGroupLaunchConfigSection({ serverGroup }: { app: any; serverGroup: any }): JSX.Element {
  const template = serverGroup.launchConfig?.instanceTemplate;
  return (
    <CollapsibleSection heading="Launch Configuration" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Instance Template</dt>
        <dd>{template?.name || '-'}</dd>
        <dt>Zones</dt>
        <dd>{(serverGroup.zones || []).join(', ') || '-'}</dd>
        <dt>Logs</dt>
        <dd>
          {serverGroup.logsLink ? (
            <a href={serverGroup.logsLink} target="_blank" rel="noopener noreferrer">
              View Google Cloud logs
            </a>
          ) : (
            '-'
          )}
        </dd>
      </dl>
    </CollapsibleSection>
  );
}

export function GceAutoscalingPolicySection({
  app,
  serverGroup,
}: {
  app: Application;
  serverGroup: IGceServerGroup;
}): JSX.Element {
  return (
    <CollapsibleSection heading="Autoscaling">
      <GceAutoscalingPolicyDetails
        application={app}
        mutationsEnabled={!CloudProviderRegistry.isDisabled('gce')}
        policy={serverGroup.autoscalingPolicy}
        serverGroup={serverGroup}
      />
    </CollapsibleSection>
  );
}

export function GceAutoHealingPolicySection({
  app,
  serverGroup,
}: {
  app: Application;
  serverGroup: IGceServerGroup;
}): JSX.Element {
  return (
    <CollapsibleSection heading="Auto-healing">
      <GceAutoHealingPolicyDetails
        application={app}
        mutationsEnabled={!CloudProviderRegistry.isDisabled('gce')}
        policy={serverGroup.autoHealingPolicy}
        serverGroup={serverGroup}
      />
    </CollapsibleSection>
  );
}

export const gceServerGroupDetailsSections = [
  GceServerGroupInformationSection,
  GceServerGroupCapacitySection,
  GceServerGroupLaunchConfigSection,
  GceAutoscalingPolicySection,
  GceAutoHealingPolicySection,
];
