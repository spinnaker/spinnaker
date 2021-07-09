import { has } from 'lodash';

import { Application } from '../../application/application.model';
import { IConfirmationModalParams } from '../../confirmationModal/confirmationModal.service';
import { ICluster, IServerGroup } from '../../domain';

export class ServerGroupWarningMessageService {
  public static addDestroyWarningMessage(
    application: Application,
    serverGroup: IServerGroup,
    params: IConfirmationModalParams,
  ): void {
    const remainingServerGroups: IServerGroup[] = this.getOtherServerGroupsInCluster(application, serverGroup);
    if (!remainingServerGroups.length) {
      params.body = `<h4 class="error-message">You are destroying the last Server Group in the Cluster.</h4>
        <dl class="dl-horizontal dl-narrow">
          <dt>Account: </dt>
          <dd>${serverGroup.account}</dd>
          <dt>Region: </dt>
          <dd>${serverGroup.region}</dd>
          <dt>Cluster: </dt>
          <dd>${serverGroup.cluster}</dd>
        </dl>`;
    }
  }

  public static addDisableWarningMessage(
    application: Application,
    serverGroup: IServerGroup,
    params: IConfirmationModalParams,
  ): void {
    if (!serverGroup.instanceCounts.up) {
      return;
    }

    const otherServerGroupsInCluster: IServerGroup[] = this.getOtherServerGroupsInCluster(application, serverGroup);
    const remainingActiveServerGroups: IServerGroup[] = otherServerGroupsInCluster.filter(
      (s) => !s.isDisabled && s.instanceCounts.up > 0,
    );
    const hasOtherInstances = otherServerGroupsInCluster.some((s) => s.instances.length > 0);

    if (hasOtherInstances || remainingActiveServerGroups.length === 0 || otherServerGroupsInCluster.length === 0) {
      const totalActiveInstances = remainingActiveServerGroups.reduce((acc: number, s: IServerGroup) => {
        return s.instanceCounts.up + acc;
      }, serverGroup.instanceCounts.up);

      const activeInstancesAfterDisable = totalActiveInstances - serverGroup.instanceCounts.up;
      const activePercentRemaining = Math.round((activeInstancesAfterDisable / totalActiveInstances) * 100);

      params.body = `<h4>You are disabling <b>${serverGroup.instanceCounts.up}</b>
            instance${serverGroup.instanceCounts.up === 1 ? '' : 's'}.</h4>
        <p>This will reduce the cluster to <b>${activePercentRemaining}</b> percent of its current capacity,
           leaving <b>${activeInstancesAfterDisable}</b> instance${activeInstancesAfterDisable === 1 ? '' : 's'} taking
            traffic.</p>
        <ul>${this.getRemainingServerGroupsForDisplay(remainingActiveServerGroups)}</ul>`;

      params.verificationLabel = `Verify the number of remaining active instances
          (<span class="verification-text">${activeInstancesAfterDisable}</span>) after disabling this server group.`;

      params.textToVerify = `${activeInstancesAfterDisable}`;
      delete params.account;
    }
  }

  private static getOtherServerGroupsInCluster(application: Application, serverGroup: IServerGroup): IServerGroup[] {
    const cluster: ICluster = application.clusters.find(
      (c: ICluster) => c.account === serverGroup.account && c.name === serverGroup.cluster,
    );
    return cluster
      ? cluster.serverGroups.filter((s) => s.region === serverGroup.region && s.name !== serverGroup.name)
      : [];
  }

  private static getRemainingServerGroupsForDisplay(serverGroups: IServerGroup[]): string {
    return serverGroups
      .sort((a, b) => b.name.localeCompare(a.name))
      .map((sg) => {
        let label = sg.name;
        if (has(sg, 'buildInfo.jenkins.number')) {
          label = `${sg.name} (build #${sg.buildInfo.jenkins.number})`;
        }
        return `<li>${label}: ${sg.instanceCounts.up} instance${sg.instanceCounts.up === 1 ? '' : 's'}</li>`;
      })
      .join('\n');
  }
}
