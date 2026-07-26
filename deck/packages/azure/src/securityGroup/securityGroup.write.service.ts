import { FirewallLabels, InfrastructureCaches, TaskExecutor } from '@spinnaker/core';
import type { Application, ISecurityGroup, ITask } from '@spinnaker/core';

type AzureSecurityGroupCommand = Record<string, any>;

function getSecurityGroupAccount(securityGroup: ISecurityGroup & AzureSecurityGroupCommand): string {
  return securityGroup.accountId || securityGroup.account || securityGroup.accountName || securityGroup.credentials;
}

export function buildUpsertCommand(
  securityGroup: ISecurityGroup & AzureSecurityGroupCommand,
  params: AzureSecurityGroupCommand = {},
): AzureSecurityGroupCommand {
  return {
    ...securityGroup,
    ...params,
    securityGroupName: securityGroup.name,
  };
}

export function buildDeleteCommand(
  securityGroup: ISecurityGroup & AzureSecurityGroupCommand,
  application: Application,
  params: AzureSecurityGroupCommand = {},
): AzureSecurityGroupCommand {
  return {
    ...params,
    type: 'deleteSecurityGroup',
    securityGroupName: securityGroup.name,
    regions: [securityGroup.region],
    credentials: getSecurityGroupAccount(securityGroup),
    appName: application.name,
  };
}

export const AzureSecurityGroupWriter = {
  upsertSecurityGroup(
    securityGroup: ISecurityGroup & AzureSecurityGroupCommand,
    application: Application,
    descriptor: string,
    params: AzureSecurityGroupCommand = {},
  ): PromiseLike<ITask> {
    const command = buildUpsertCommand(securityGroup, params);

    const operation = TaskExecutor.executeTask({
      job: [command],
      application,
      description: `${descriptor} ${FirewallLabels.get('Firewall')}: ${securityGroup.name}`,
    });

    InfrastructureCaches.clearCache('securityGroups');

    return operation;
  },

  deleteSecurityGroup(
    securityGroup: ISecurityGroup & AzureSecurityGroupCommand,
    application: Application,
    params: AzureSecurityGroupCommand = {},
  ): PromiseLike<ITask> {
    const command = buildDeleteCommand(securityGroup, application, params);

    const operation = TaskExecutor.executeTask({
      job: [command],
      application,
      description: `Delete ${FirewallLabels.get('Firewalls')}: ${securityGroup.name}`,
    });

    InfrastructureCaches.clearCache('securityGroups');

    return operation;
  },
};
