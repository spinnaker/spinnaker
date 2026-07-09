import React from 'react';
import { Observable } from 'rxjs';

import type { IServerGroupDetailsProps, IServerGroupDetailsSectionProps } from '@spinnaker/core';
import { AccountTag, CollapsibleSection, ServerGroupReader, timestamp } from '@spinnaker/core';

import { DcosJsonLink, DcosLink, DcosMapSection } from '../../common/DcosDetails';
import { DcosCloneServerGroupModal } from '../configure/DcosCloneServerGroupModal';
import { dcosProxyUiService } from '../../proxy/ui.service';

function findServerGroupSummary(props: IServerGroupDetailsProps): PromiseLike<any> {
  const { app, serverGroup } = props;
  return app.ready().then(() => {
    let summary = app.serverGroups.data.find((toCheck: any) => {
      return (
        toCheck.name === serverGroup.name &&
        toCheck.account === serverGroup.accountId &&
        toCheck.region === serverGroup.region
      );
    });

    if (!summary) {
      app.loadBalancers.data.some((loadBalancer: any) => {
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
  });
}

export function dcosServerGroupDetailsGetter(props: IServerGroupDetailsProps, autoClose: () => void): Observable<any> {
  const { app, serverGroup: serverGroupInfo } = props;
  return new Observable<any>((observer) => {
    findServerGroupSummary(props).then((summary) => {
      ServerGroupReader.getServerGroup(
        app.name,
        serverGroupInfo.accountId,
        serverGroupInfo.region,
        serverGroupInfo.name,
      ).then((serverGroup: any) => {
        Object.assign(serverGroup, summary, { account: serverGroupInfo.accountId });

        if (serverGroup && Object.keys(serverGroup).length > 0) {
          observer.next(serverGroup);
        } else {
          autoClose();
        }
      }, autoClose);
    }, autoClose);
  });
}

function dcosServiceLink(serverGroup: any): string {
  const cluster = serverGroup.dcosCluster || serverGroup.region;
  const host = serverGroup.dcosClusterUrl || serverGroup.dcosUrl || serverGroup.host;
  if (!host) {
    return undefined;
  }
  return dcosProxyUiService.buildLink(host, serverGroup.account, cluster, serverGroup.group || serverGroup.name);
}

export function DcosServerGroupActions({ app, serverGroup }: any) {
  const clone = () => {
    const command = serverGroup.deployDescription || { ...serverGroup, viewState: { mode: 'clone' } };
    DcosCloneServerGroupModal.show({ application: app, command });
  };

  return (
    <div className="dropdown">
      <button className="btn btn-sm btn-primary dropdown-toggle" data-toggle="dropdown">
        Server Group Actions <span className="caret" />
      </button>
      <ul className="dropdown-menu dropdown-menu-right">
        <li>
          <a className="clickable" onClick={clone}>
            Clone
          </a>
        </li>
      </ul>
    </div>
  );
}

export function DcosServerGroupInformationSection({ serverGroup }: IServerGroupDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Information" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Created</dt>
        <dd>{timestamp(serverGroup.createdTime)}</dd>
        <dt>Account</dt>
        <dd>
          <AccountTag account={serverGroup.account} />
        </dd>
        <dt>Region</dt>
        <dd>{serverGroup.region || '-'}</dd>
        <dt>DC/OS Cluster</dt>
        <dd>{serverGroup.dcosCluster || '-'}</dd>
        <dt>Group</dt>
        <dd>{serverGroup.group || '-'}</dd>
        <dt>Kind</dt>
        <dd>{serverGroup.kind || serverGroup.type || 'service'}</dd>
        <dt>JSON</dt>
        <dd>
          <DcosJsonLink value={serverGroup} />
        </dd>
        <dt>DC/OS UI</dt>
        <DcosLink href={dcosServiceLink(serverGroup)} />
      </dl>
    </CollapsibleSection>
  );
}

export function DcosServerGroupCapacitySection({ serverGroup }: IServerGroupDetailsSectionProps) {
  const capacity: any = serverGroup.capacity || {};
  return (
    <CollapsibleSection heading="Capacity" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Desired</dt>
        <dd>{capacity['desired'] ?? serverGroup.desiredCapacity ?? '-'}</dd>
        <dt>Running</dt>
        <dd>{serverGroup.runningTasks ?? serverGroup.instances?.length ?? '-'}</dd>
      </dl>
    </CollapsibleSection>
  );
}

export function DcosServerGroupStatusSection({ serverGroup }: IServerGroupDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Status and Health" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Status</dt>
        <dd>{serverGroup.status || (serverGroup.isDisabled ? 'Disabled' : 'Active')}</dd>
        <dt>Health</dt>
        <dd>{serverGroup.health?.state || serverGroup.healthState || '-'}</dd>
      </dl>
    </CollapsibleSection>
  );
}

export function DcosServerGroupResourcesSection({ serverGroup }: IServerGroupDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Resources" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>CPUs</dt>
        <dd>{serverGroup.cpus ?? serverGroup.deployDescription?.cpus ?? '-'}</dd>
        <dt>Memory</dt>
        <dd>{serverGroup.mem ?? serverGroup.deployDescription?.mem ?? '-'}</dd>
        <dt>Disk</dt>
        <dd>{serverGroup.disk ?? serverGroup.deployDescription?.disk ?? '-'}</dd>
        <dt>GPUs</dt>
        <dd>{serverGroup.gpus ?? serverGroup.deployDescription?.gpus ?? '-'}</dd>
      </dl>
    </CollapsibleSection>
  );
}

export function DcosServerGroupEnvironmentSection({ serverGroup }: IServerGroupDetailsSectionProps) {
  return <DcosMapSection heading="Environment" value={serverGroup.env || serverGroup.deployDescription?.env} />;
}

export function DcosServerGroupLabelsSection({ serverGroup }: IServerGroupDetailsSectionProps) {
  return <DcosMapSection heading="Labels" value={serverGroup.labels || serverGroup.deployDescription?.labels} />;
}

export const dcosServerGroupDetailsSections = [
  DcosServerGroupInformationSection,
  DcosServerGroupCapacitySection,
  DcosServerGroupStatusSection,
  DcosServerGroupResourcesSection,
  DcosServerGroupEnvironmentSection,
  DcosServerGroupLabelsSection,
];
