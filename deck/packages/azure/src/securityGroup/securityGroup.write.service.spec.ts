import { InfrastructureCaches, TaskExecutor } from '@spinnaker/core';

import { AzureSecurityGroupWriter, buildDeleteCommand, buildUpsertCommand } from './securityGroup.write.service';

describe('AzureSecurityGroupWriter', () => {
  const application = { name: 'fnord' } as any;
  const securityGroup = {
    accountId: 'azure-account',
    cloudProvider: 'azure',
    name: 'azure-sg',
    region: 'westus',
    rules: [{ protocol: 'tcp', port: 443 }],
    vpcId: 'sg-vnet',
  } as any;

  it('builds an upsert command without overwriting explicit params', () => {
    const params = { cloudProvider: 'explicit-provider', rules: [], type: 'customUpsertType' };

    const command = buildUpsertCommand(securityGroup, params);

    expect(command).toEqual(
      jasmine.objectContaining({
        accountId: 'azure-account',
        cloudProvider: 'explicit-provider',
        name: 'azure-sg',
        region: 'westus',
        rules: [],
        securityGroupName: 'azure-sg',
        type: 'customUpsertType',
        vpcId: 'sg-vnet',
      }),
    );
    expect(params).toEqual({ cloudProvider: 'explicit-provider', rules: [], type: 'customUpsertType' });
  });

  it('builds a delete command with the Azure security group payload', () => {
    const command = buildDeleteCommand(securityGroup, application, { cloudProvider: 'azure', vpcId: 'explicit-vnet' });

    expect(command).toEqual(
      jasmine.objectContaining({
        appName: 'fnord',
        cloudProvider: 'azure',
        credentials: 'azure-account',
        regions: ['westus'],
        securityGroupName: 'azure-sg',
        type: 'deleteSecurityGroup',
        vpcId: 'explicit-vnet',
      }),
    );
  });

  it('builds a delete command from legacy account identity fields', () => {
    const command = buildDeleteCommand(
      { ...securityGroup, accountId: undefined, accountName: 'legacy-account' },
      application,
      { cloudProvider: 'azure' },
    );

    expect(command.credentials).toBe('legacy-account');
  });

  it('executes an upsert task and clears the security group cache', () => {
    const task = Promise.resolve({}) as any;
    const executeTask = spyOn(TaskExecutor, 'executeTask').and.returnValue(task);
    const clearCache = spyOn(InfrastructureCaches, 'clearCache');

    const result = AzureSecurityGroupWriter.upsertSecurityGroup(securityGroup, application, 'Create', {
      cloudProvider: 'azure',
    });

    expect(result).toBe(task);
    expect(executeTask).toHaveBeenCalledWith(
      jasmine.objectContaining({
        application,
        description: 'Create Firewall: azure-sg',
        job: [jasmine.objectContaining({ cloudProvider: 'azure', securityGroupName: 'azure-sg' })],
      }),
    );
    expect(clearCache).toHaveBeenCalledWith('securityGroups');
  });

  it('executes a delete task and clears the security group cache', () => {
    const task = Promise.resolve({}) as any;
    const executeTask = spyOn(TaskExecutor, 'executeTask').and.returnValue(task);
    const clearCache = spyOn(InfrastructureCaches, 'clearCache');

    const result = AzureSecurityGroupWriter.deleteSecurityGroup(securityGroup, application, { vpcId: 'explicit-vnet' });

    expect(result).toBe(task);
    expect(executeTask).toHaveBeenCalledWith(
      jasmine.objectContaining({
        application,
        description: 'Delete Firewalls: azure-sg',
        job: [
          jasmine.objectContaining({
            securityGroupName: 'azure-sg',
            type: 'deleteSecurityGroup',
            vpcId: 'explicit-vnet',
          }),
        ],
      }),
    );
    expect(clearCache).toHaveBeenCalledWith('securityGroups');
  });
});
