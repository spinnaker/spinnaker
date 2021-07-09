import { IAmazonServerGroup, IScalingProcess } from '../../../domain';

export class AutoScalingProcessService {
  public static getDisabledDate(serverGroup: IAmazonServerGroup): number {
    if (serverGroup.isDisabled) {
      const processes = this.normalizeScalingProcesses(serverGroup);
      const disabledProcess = processes.find((process) => process.name === 'AddToLoadBalancer' && !process.enabled);
      if (disabledProcess) {
        return disabledProcess.suspensionDate;
      }
    }
    return null;
  }

  public static normalizeScalingProcesses(serverGroup: IAmazonServerGroup): IScalingProcess[] {
    if (!serverGroup.asg || !serverGroup.asg.suspendedProcesses) {
      return [];
    }
    const disabled = serverGroup.asg.suspendedProcesses;
    const allProcesses = this.listProcesses();
    return allProcesses.map((process) => {
      const disabledProcess = disabled.find((p) => p.processName === process.name);
      const scalingProcess: IScalingProcess = {
        name: process.name,
        enabled: !disabledProcess,
        description: process.description,
      };
      if (disabledProcess) {
        const suspensionDate = disabledProcess.suspensionReason.replace('User suspended at ', '');
        scalingProcess.suspensionDate = new Date(suspensionDate).getTime();
      }
      return scalingProcess;
    });
  }

  public static listProcesses(): IScalingProcess[] {
    return [
      {
        name: 'Launch',
        description:
          'Controls if new instances should be launched into the ASG. If this is disabled, scale-up ' +
          'events will not produce new instances.',
      },
      {
        name: 'Terminate',
        description: 'Controls if instances should be terminated during a scale-down event.',
      },
      {
        name: 'AddToLoadBalancer',
        description: 'Controls if new instances should be added to the ASG’s ELB.',
      },
      {
        name: 'AlarmNotification',
        description: 'This disables autoscaling.',
      },
      {
        name: 'AZRebalance',
        description:
          'Controls whether AWS should attempt to maintain an even distribution of instances across all ' +
          'healthy Availability Zones configured for the ASG.',
      },
      {
        name: 'HealthCheck',
        description: 'If disabled, the instance’s health will no longer be reported to the autoscaling processor.',
      },
      {
        name: 'ReplaceUnhealthy',
        description: 'Controls whether instances should be replaced if they failed the health check.',
      },
      {
        name: 'ScheduledActions',
        description: 'Controls whether scheduled actions should be executed.',
      },
    ];
  }
}
