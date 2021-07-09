import { intersection, map } from 'lodash';

import { IServerGroup, ITask } from '../domain';

export type ITaskMatcher = (task: ITask, serverGroup: IServerGroup) => boolean;

/**
 * Match running tasks for an application to a specific server group.
 * This allows the tasks to be displayed in the server group details, or
 * as a popover on the server group header.
 */
export class TaskMatcher {
  private customMatchers: { [type: string]: ITaskMatcher } = {
    createcopylastasg: createcopylastasgMatcher,
    createdeploy: createdeployMatcher,
    rollbackServerGroup: rollbackServerGroupTaskMatcher,
  };

  private instanceIdMatchers = [
    'deregisterinstancesfromloadbalancer',
    'disableinstances',
    'disableinstancesindiscovery',
    'enableinstancesindiscovery',
    'rebootinstances',
    'registerinstanceswithloadbalancer',
    'terminateinstances',
  ];

  private baseTaskMatchers = [
    'destroyasg',
    'destroyservergroup',
    'disableasg',
    'disablegoogleservergroup',
    'disableservergroup',
    'enableasg',
    'enablegoogleservergroup',
    'enableservergroup',
    'resizeasg',
    'resizeservergroup',
    'resumeasgprocessesdescription',
  ];

  public addMatcher(stageName: string, matcher: ITaskMatcher | 'instanceIdMatchers' | 'baseTaskMatchers') {
    if (typeof matcher === 'function') {
      this.customMatchers[stageName] = matcher;
    } else if (matcher === 'instanceIdMatchers') {
      this.instanceIdMatchers.push(stageName);
    } else if (matcher === 'baseTaskMatchers') {
      this.baseTaskMatchers.push(stageName);
    }
  }

  public taskMatches(task: ITask, serverGroup: IServerGroup) {
    const matchers: { [type: string]: ITaskMatcher } = { ...this.customMatchers };
    this.instanceIdMatchers.forEach((m) => (matchers[m] = instanceIdsTaskMatcher));
    this.baseTaskMatchers.forEach((m) => (matchers[m] = baseTaskMatcher));

    const firstStage = task.execution?.stages?.[0] ?? { context: {} };
    const notificationType: string =
      firstStage.context['notification.type'] || firstStage.type || task.getValueFor('notification.type');

    const matcher = notificationType && (matchers[notificationType] || matchers[notificationType.toLowerCase()]);
    return !!matcher && matcher(task, serverGroup);
  }
}

function createcopylastasgMatcher(task: ITask, serverGroup: IServerGroup): boolean {
  const source: any = task.getValueFor('source');
  const targetAccount: string = task.getValueFor('deploy.account.name');
  const targetRegion: string = task.getValueFor('availabilityZones')
    ? Object.keys(task.getValueFor('availabilityZones'))[0]
    : null;
  const deployedServerGroups: { [region: string]: string[] } = task.getValueFor('deploy.server.groups');
  const targetServerGroup: string =
    targetRegion && deployedServerGroups && deployedServerGroups[targetRegion]
      ? deployedServerGroups[targetRegion][0]
      : null;
  const sourceServerGroup: string = source.asgName;
  const sourceAccount: string = source.account;
  const sourceRegion: string = source.region;

  const targetMatches =
    serverGroup.account === targetAccount &&
    serverGroup.region === targetRegion &&
    serverGroup.name === targetServerGroup;
  const sourceMatches =
    serverGroup.account === sourceAccount &&
    serverGroup.region === sourceRegion &&
    serverGroup.name === sourceServerGroup;
  return targetMatches || sourceMatches;
}

function createdeployMatcher(task: ITask, serverGroup: IServerGroup): boolean {
  const account: string = task.getValueFor('deploy.account.name');
  const region: string = task.getValueFor('deploy.server.groups')
    ? Object.keys(task.getValueFor('deploy.server.groups'))[0]
    : null;
  const serverGroupName: string = serverGroup && region ? task.getValueFor('deploy.server.groups')[region][0] : null;

  if (account && serverGroup && region) {
    return serverGroup.account === account && serverGroup.region === region && serverGroup.name === serverGroupName;
  }
  return false;
}

function baseTaskMatcher(task: ITask, serverGroup: IServerGroup): boolean {
  const taskRegion: string = task.getValueFor('regions')
    ? task.getValueFor('regions')[0]
    : task.getValueFor('region') || null;
  return (
    serverGroup.account === task.getValueFor('credentials') &&
    serverGroup.region === taskRegion &&
    serverGroup.name === task.getValueFor('asgName')
  );
}

function instanceIdsTaskMatcher(task: ITask, serverGroup: IServerGroup): boolean {
  if (task.getValueFor('region') === serverGroup.region && task.getValueFor('credentials') === serverGroup.account) {
    if (task.getValueFor('knownInstanceIds')) {
      return intersection(map(serverGroup.instances, 'id'), task.getValueFor('knownInstanceIds')).length > 0;
    } else {
      return intersection(map(serverGroup.instances, 'id'), task.getValueFor('instanceIds')).length > 0;
    }
  }
  return false;
}

function rollbackServerGroupTaskMatcher(task: ITask, serverGroup: IServerGroup): boolean {
  const account: string = task.getValueFor('credentials');
  const region: string = task.getValueFor('region');

  if (account && serverGroup.account === account && region && serverGroup.region === region) {
    return (
      serverGroup.name === task.getValueFor('targetop.asg.disableServerGroup.name') ||
      serverGroup.name === task.getValueFor('targetop.asg.enableServerGroup.name')
    );
  }
  return false;
}

export const taskMatcher = new TaskMatcher();
