import React from 'react';
import { Dropdown } from 'react-bootstrap';
import { Observable } from 'rxjs';
import type { Subscriber } from 'rxjs';

import type {
  IRouterInjectedProps,
  IServerGroupActionsProps,
  IServerGroupDetailsProps,
  IServerGroupDetailsSectionProps,
} from '@spinnaker/core';
import {
  AccountTag,
  CloudProviderRegistry,
  CollapsibleSection,
  ConfirmationModalService,
  FirewallLabels,
  HealthCounts,
  ServerGroupReader,
  ServerGroupWarningMessageService,
  timestamp,
  useDeckRuntimeServices,
  withRouter,
} from '@spinnaker/core';

import { AzureRollbackServerGroupModal } from './rollback/RollbackServerGroupModal';

function findServerGroupSummary(props: IServerGroupDetailsProps): PromiseLike<any> {
  const { app, serverGroup } = props;
  const findSummary = () => {
    let summary = app.serverGroups.data.find((toCheck: any) => {
      return (
        toCheck.name === serverGroup.name &&
        toCheck.account === serverGroup.accountId &&
        toCheck.region === serverGroup.region
      );
    });

    if (!summary) {
      app.loadBalancers.data.some((loadBalancer: any) => {
        if (loadBalancer.account !== serverGroup.accountId || loadBalancer.region !== serverGroup.region) {
          return false;
        }
        return (loadBalancer.serverGroups || []).some((possibleServerGroup: any) => {
          if (possibleServerGroup.name === serverGroup.name) {
            summary = possibleServerGroup;
            return true;
          }
          return false;
        });
      });
    }

    return summary;
  };

  return app.ready ? app.ready().then(findSummary) : Promise.resolve(findSummary());
}

function decorateAzureServerGroup(serverGroup: any, app: any, accountId: string, region: string): any {
  if (serverGroup.image?.description) {
    serverGroup.image.description.split(', ').forEach((tag: string) => {
      const [key, value] = tag.split('=');
      if (key === 'ancestor_name' && value) {
        serverGroup.image.baseImage = value;
      }
    });
  }

  const securityGroupIds = serverGroup.launchConfig?.securityGroups || [];
  if (securityGroupIds.length) {
    serverGroup.securityGroups = securityGroupIds
      .map((id: string) => {
        return app.securityGroups.data.find((securityGroup: any) => {
          return (
            securityGroup.accountName === accountId &&
            securityGroup.region === region &&
            (securityGroup.id === id || securityGroup.name === id)
          );
        });
      })
      .filter(Boolean);
  }

  return serverGroup;
}

export function azureServerGroupDetailsGetter(props: IServerGroupDetailsProps, autoClose: () => void): Observable<any> {
  const { app, serverGroup: serverGroupInfo } = props;
  return new Observable<any>((observer: Subscriber<any>) => {
    findServerGroupSummary(props).then(
      (summary) => {
        if (!summary) {
          autoClose();
          observer.complete();
          return;
        }

        ServerGroupReader.getServerGroup(
          app.name,
          serverGroupInfo.accountId,
          serverGroupInfo.region,
          serverGroupInfo.name,
        ).then(
          (details: any) => {
            if (!details || Object.keys(details).length === 0) {
              autoClose();
              observer.complete();
              return;
            }

            const serverGroup = decorateAzureServerGroup(
              Object.assign(details, summary, { account: serverGroupInfo.accountId }),
              app,
              serverGroupInfo.accountId,
              serverGroupInfo.region,
            );
            observer.next(serverGroup);
          },
          () => {
            autoClose();
            observer.complete();
          },
        );
      },
      () => {
        autoClose();
        observer.complete();
      },
    );
  });
}

export function AzureServerGroupActionsComponent({
  app,
  serverGroup,
  stateService,
}: IServerGroupActionsProps & IRouterInjectedProps) {
  const runtimeServices = useDeckRuntimeServices();
  const { serverGroupCommandBuilder, serverGroupWriter } = runtimeServices;
  const destroyServerGroup = (): void => {
    const stateParams = {
      name: serverGroup.name,
      accountId: serverGroup.account,
      region: serverGroup.region,
    };

    const confirmationModalParams = {
      header: `Really destroy ${serverGroup.name}?`,
      buttonText: `Destroy ${serverGroup.name}`,
      account: serverGroup.account,
      taskMonitorConfig: {
        application: app,
        title: `Destroying ${serverGroup.name}`,
        onTaskComplete: () => {
          if (stateService.includes('**.serverGroup', stateParams)) {
            stateService.go('^');
          }
        },
      },
      submitMethod: (params: any) => serverGroupWriter.destroyServerGroup(serverGroup, app, params),
    };

    ServerGroupWarningMessageService.addDestroyWarningMessage(app, serverGroup, confirmationModalParams);
    ConfirmationModalService.confirm(confirmationModalParams);
  };

  const disableServerGroup = (): void => {
    const confirmationModalParams = {
      header: `Really disable ${serverGroup.name}?`,
      buttonText: `Disable ${serverGroup.name}`,
      account: serverGroup.account,
      taskMonitorConfig: {
        application: app,
        title: `Disabling ${serverGroup.name}`,
      },
      submitMethod: (params: any) => serverGroupWriter.disableServerGroup(serverGroup, app.name, params),
    };

    ServerGroupWarningMessageService.addDisableWarningMessage(app, serverGroup, confirmationModalParams);
    ConfirmationModalService.confirm(confirmationModalParams);
  };

  const enableServerGroup = (): void => {
    ConfirmationModalService.confirm({
      header: `Really enable ${serverGroup.name}?`,
      buttonText: `Enable ${serverGroup.name}`,
      account: serverGroup.account,
      taskMonitorConfig: {
        application: app,
        title: `Enabling ${serverGroup.name}`,
      },
      submitMethod: (params: any) =>
        serverGroupWriter.enableServerGroup(serverGroup, app, {
          ...params,
          interestingHealthProviderNames: [],
        }),
    });
  };

  const rollbackServerGroup = (): void => {
    const cluster = (app.clusters || []).find(
      (candidate: any) => candidate.name === serverGroup.cluster && candidate.account === serverGroup.account,
    );
    const disabledServerGroups = (cluster?.serverGroups || []).filter((candidate: any) => {
      return candidate.isDisabled && candidate.region === serverGroup.region;
    });
    AzureRollbackServerGroupModal.show({ application: app, serverGroup, disabledServerGroups }, runtimeServices);
  };

  const cloneServerGroup = (): void => {
    const CloneServerGroupModal = CloudProviderRegistry.getValue('azure', 'serverGroup.CloneServerGroupModal');
    if (CloneServerGroupModal?.show) {
      serverGroupCommandBuilder.buildServerGroupCommandFromExisting(app, serverGroup).then((command: any) => {
        CloneServerGroupModal.show({ application: app, command, title: `Clone ${serverGroup.name}` }, runtimeServices);
      });
    }
  };

  return (
    <Dropdown className="dropdown" id="azure-server-group-actions-dropdown">
      <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">Server Group Actions</Dropdown.Toggle>
      <Dropdown.Menu className="dropdown-menu">
        {!serverGroup.isDisabled && (
          <li>
            <a className="clickable" onClick={rollbackServerGroup}>
              Rollback
            </a>
          </li>
        )}
        <li>
          <a className="clickable" onClick={disableServerGroup}>
            Disable
          </a>
        </li>
        <li>
          <a className="clickable" onClick={enableServerGroup}>
            Enable
          </a>
        </li>
        <li>
          <a className="clickable" onClick={destroyServerGroup}>
            Destroy
          </a>
        </li>
        <li>
          <a className="clickable" onClick={cloneServerGroup}>
            Clone
          </a>
        </li>
      </Dropdown.Menu>
    </Dropdown>
  );
}

export const AzureServerGroupActions = withRouter(AzureServerGroupActionsComponent);

export function AzureServerGroupInformationSection({ serverGroup }: IServerGroupDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Server Group Information" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Created</dt>
        <dd>{timestamp(serverGroup.createdTime)}</dd>
        <dt>Region</dt>
        <dd>
          <AccountTag account={serverGroup.account} />
          {serverGroup.region}
        </dd>
        <dt>Image</dt>
        <dd>{serverGroup.image?.imageName || '-'}</dd>
        <dt>SKU</dt>
        <dd>{serverGroup.sku?.name || '-'}</dd>
      </dl>
    </CollapsibleSection>
  );
}

export function AzureServerGroupSizeSection({ serverGroup }: IServerGroupDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Size" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Current</dt>
        <dd>{serverGroup.instances?.length ?? 0}</dd>
      </dl>
    </CollapsibleSection>
  );
}

export function AzureServerGroupHealthSection({ serverGroup }: IServerGroupDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Health" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Instances</dt>
        <dd>
          <HealthCounts container={serverGroup.instanceCounts} className="pull-left" />
        </dd>
      </dl>
    </CollapsibleSection>
  );
}

export function AzureServerGroupFirewallsSection({ serverGroup }: IServerGroupDetailsSectionProps) {
  return (
    <CollapsibleSection heading={FirewallLabels.get('Firewalls')}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Name</dt>
        <dd>{serverGroup.securityGroupName || '-'}</dd>
      </dl>
    </CollapsibleSection>
  );
}

export const azureServerGroupDetailsSections = [
  AzureServerGroupInformationSection,
  AzureServerGroupSizeSection,
  AzureServerGroupHealthSection,
  AzureServerGroupFirewallsSection,
];
