import {module} from 'angular';
import {has} from 'lodash';

import {ServerGroup} from 'core/domain';
import {Application} from 'core/application/application.model';
import {ICluster} from 'core/domain/ICluster';
import {IConfirmationModalParams} from 'core/confirmationModal/confirmationModal.service';

export class ServerGroupWarningMessageService {

  public addDestroyWarningMessage(application: Application, serverGroup: ServerGroup, params: IConfirmationModalParams): void {
    const remainingServerGroups: ServerGroup[] = this.getOtherServerGroupsInCluster(application, serverGroup);
    if (!remainingServerGroups.length) {
      params.body = `
        <h4 class="error-message">You are destroying the last Server Group in the Cluster.</h4>
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

  public addDisableWarningMessage(application: Application, serverGroup: ServerGroup, params: IConfirmationModalParams): void {

    if (!serverGroup.instanceCounts.up) {
      return;
    }

    const remainingActiveServerGroups: ServerGroup[] = this.getOtherServerGroupsInCluster(application, serverGroup)
      .filter(s => !s.isDisabled && s.instanceCounts.up > 0);

    const hasOtherInstances = this.getOtherServerGroupsInCluster(application, serverGroup).some(s => s.instances.length > 0);

    if (hasOtherInstances) {
      const totalActiveInstances = remainingActiveServerGroups.reduce((acc: number, s: ServerGroup) => {
        return s.instanceCounts.up + acc;
      }, serverGroup.instanceCounts.up);

      const activeInstancesAfterDisable = totalActiveInstances - serverGroup.instanceCounts.up;
      const activePercentRemaining = Math.round(activeInstancesAfterDisable / totalActiveInstances * 100);

      params.body = `
        <h4>You are disabling <b>${serverGroup.instanceCounts.up}</b> 
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

  private getOtherServerGroupsInCluster(application: Application, serverGroup: ServerGroup): ServerGroup[] {
    const cluster: ICluster = application.clusters
      .find((c: ICluster) => c.account === serverGroup.account && c.name === serverGroup.cluster);
    return cluster ? cluster.serverGroups
        .filter(s => s.region === serverGroup.region && s.name !== serverGroup.name) : [];
  }

  private getRemainingServerGroupsForDisplay(serverGroups: ServerGroup[]): string {
    return serverGroups
      .sort((a, b) => b.name.localeCompare(a.name))
      .map(sg => {
        let label = sg.name;
        if (has(sg, 'buildInfo.jenkins.number')) {
          label = `${sg.name} (build #${sg.buildInfo.jenkins.number})`;
        }
        return `<li>${label}: ${sg.instanceCounts.up} instance${sg.instanceCounts.up === 1 ? '' : 's'}</li>`;
    }).join('\n');
  }
}

export const SERVER_GROUP_WARNING_MESSAGE_SERVICE = 'spinnaker.core.serverGroup.details.warningMessage.service';
module(SERVER_GROUP_WARNING_MESSAGE_SERVICE, [])
  .service('serverGroupWarningMessageService', ServerGroupWarningMessageService);
