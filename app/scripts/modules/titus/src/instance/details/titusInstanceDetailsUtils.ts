import {
  getAllTargetGroups,
  IAmazonApplicationLoadBalancer,
  IAmazonHealth,
  IAmazonNetworkLoadBalancer,
  IAmazonTargetGroupHealth,
  ITargetGroup,
} from '@spinnaker/amazon';
import {
  Action,
  Application,
  ConfirmationModalService,
  IInstance,
  IJob,
  InstanceWriter,
  ReactInjector,
} from '@spinnaker/core';

export const applyTargetGroupInfoToHealthMetric = (
  metricGroups: IAmazonTargetGroupHealth[],
  targetGroups: ITargetGroup[],
  account: string,
) => {
  return metricGroups.map((tg) => {
    const group = targetGroups.find(
      (g) => (g.name === tg.name || g.name === tg.targetGroupName) && g.account === account,
    );
    const useTrafficPort = group?.healthCheckPort === 'traffic-port' || !group?.healthCheckPort;
    return {
      ...tg,
      name: group?.name,
      healthCheckPath: group
        ? `:${useTrafficPort ? group?.port : group.healthCheckPort}${group?.healthCheckPath || ''}`
        : undefined,
      healthCheckProtocol: group?.healthCheckProtocol?.toLowerCase(),
    };
  });
};

export const extractHealthMetrics = (
  health: IAmazonHealth[],
  loadBalancers: IAmazonApplicationLoadBalancer[] | IAmazonNetworkLoadBalancer[],
  account: string,
): IAmazonHealth[] => {
  const displayableMetrics = (health || []).filter((m) => m.state !== 'Unknown');
  const allTargetGroups = getAllTargetGroups(loadBalancers);

  return displayableMetrics.map((metric) => {
    if (metric.type === 'TargetGroup') {
      const augmentedGroups = applyTargetGroupInfoToHealthMetric(metric.targetGroups, allTargetGroups, account);
      return { ...metric, targetGroups: augmentedGroups } as IAmazonHealth;
    }

    return metric;
  });
};

const terminateInstance = (instance: IInstance, app: Application) => {
  return () => {
    const taskMonitorConfig = {
      application: app,
      title: `Terminating ${instance.id}`,
      onTaskComplete: () => {
        if (ReactInjector.$state.includes('**.instanceDetails', { id: instance.id })) {
          ReactInjector.$state.go('^');
        }
      },
    };

    const submitMethod = () => {
      const params: IJob = { cloudProvider: 'titus' };
      if (instance.serverGroup) {
        params.serverGroupName = instance.serverGroup;
      }
      return InstanceWriter.terminateInstance(instance, app, params);
    };

    ConfirmationModalService.confirm({
      header: `Really terminate ${instance.id}?`,
      buttonText: `Terminate ${instance.id}`,
      account: instance.account,
      taskMonitorConfig,
      submitMethod,
    });
  };
};

const terminateInstanceAndShrinkServerGroup = (instance: IInstance, app: Application) => {
  return () => {
    const taskMonitorConfig = {
      application: app,
      title: `Terminating ${instance.id} and shrinking server group`,
      onTaskComplete: () => {
        if (ReactInjector.$state.includes('**.instanceDetails', { instanceId: instance.id })) {
          ReactInjector.$state.go('^');
        }
      },
    };

    const submitMethod = () => {
      return InstanceWriter.terminateInstancesAndShrinkServerGroups(
        [
          {
            cloudProvider: instance.cloudProvider,
            instanceIds: [instance.id],
            account: instance.account,
            region: instance.region,
            serverGroup: instance.serverGroup,
            instances: [instance],
          },
        ],
        app,
      );
    };

    ConfirmationModalService.confirm({
      header: `Really terminate ${instance.id} and shrink ${instance.serverGroup}?`,
      buttonText: `Terminate ${instance.id} and shrink ${instance.serverGroup}`,
      account: instance.account,
      taskMonitorConfig,
      submitMethod,
    });
  };
};

const enableInstanceInDiscovery = (instance: IInstance, app: Application) => {
  return () => {
    const taskMonitorConfig = {
      application: app,
      title: `Enabling ${instance.id} in discovery`,
    };

    const submitMethod = () => InstanceWriter.enableInstanceInDiscovery(instance, app);

    ConfirmationModalService.confirm({
      header: `Really enable ${instance.id} in discovery?`,
      buttonText: `Enable ${instance.id}`,
      account: instance.account,
      taskMonitorConfig,
      submitMethod,
    });
  };
};

const disableInstanceInDiscovery = (instance: IInstance, app: Application) => {
  return () => {
    const taskMonitorConfig = {
      application: app,
      title: `Disabling ${instance.id} in discovery`,
    };

    const submitMethod = () => InstanceWriter.disableInstanceInDiscovery(instance, app);

    ConfirmationModalService.confirm({
      header: `Really disable ${instance.id} in discovery?`,
      buttonText: `Disable ${instance.id}`,
      account: instance.account,
      taskMonitorConfig,
      submitMethod,
    });
  };
};

export const buildTaskActions = (instance: IInstance, app: Application): Action[] => {
  const taskActions = [
    { label: 'Terminate', triggerAction: terminateInstance(instance, app) },
    { label: 'Terminate and Shrink Server Group', triggerAction: terminateInstanceAndShrinkServerGroup(instance, app) },
  ];

  const discoveryHealth = (instance.health || []).filter((h) => h.type === 'Discovery');

  if (discoveryHealth.length && discoveryHealth[0].state === 'OutOfService') {
    taskActions.push({
      label: 'Enable in Discovery',
      triggerAction: enableInstanceInDiscovery(instance, app),
    });
  }

  const hasHealthState = (discoveryHealth || []).some((h) => h.state === 'Up' || h.state === 'Down');
  if (hasHealthState) {
    taskActions.push({
      label: 'Disable in Discovery',
      triggerAction: disableInstanceInDiscovery(instance, app),
    });
  }

  return taskActions;
};
