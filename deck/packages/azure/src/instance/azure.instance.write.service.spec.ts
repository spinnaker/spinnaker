import { mock } from 'angular';

import { ApplicationModelBuilder, ServerGroupReader, TaskExecutor } from '@spinnaker/core';

import { AzureInstanceWriter } from './azure.instance.write.service';

describe('AzureInstanceWriter', () => {
  let application: any;
  let instance: any;

  beforeEach(
    mock.inject(() => {
      application = ApplicationModelBuilder.createApplicationForTests('app');
      instance = {
        id: 'myapp-dev-v086_3',
        account: 'azure-cred1',
        region: 'westus',
        serverGroup: 'myapp-dev-v086',
        cloudProvider: 'azure',
        health: [],
      };
      spyOn(TaskExecutor, 'executeTask').and.returnValue(Promise.resolve({}));
    }),
  );

  it('sets cloudProvider and serverGroupName when terminating', () => {
    AzureInstanceWriter.terminateInstance(instance, application);

    const job = (TaskExecutor.executeTask as jasmine.Spy).calls.mostRecent().args[0].job[0];
    expect(job.type).toBe('terminateInstances');
    expect(job.cloudProvider).toBe('azure');
    expect(job.serverGroupName).toBe('myapp-dev-v086');
    expect(job.instanceIds).toEqual(['myapp-dev-v086_3']);
  });

  it('sets cloudProvider and serverGroupName when rebooting', () => {
    AzureInstanceWriter.rebootInstance(instance, application);

    const job = (TaskExecutor.executeTask as jasmine.Spy).calls.mostRecent().args[0].job[0];
    expect(job.type).toBe('rebootInstances');
    expect(job.cloudProvider).toBe('azure');
    expect(job.serverGroupName).toBe('myapp-dev-v086');
  });

  it('derives shrink parameters from capacity rather than asg', async () => {
    spyOn(ServerGroupReader, 'getServerGroup').and.returnValue(
      Promise.resolve({ capacity: { min: 2, max: 2, desired: 2 } }) as any,
    );

    await AzureInstanceWriter.terminateInstanceAndShrinkServerGroup(instance, application);

    const job = (TaskExecutor.executeTask as jasmine.Spy).calls.mostRecent().args[0].job[0];
    expect(job.type).toBe('terminateInstanceAndDecrementServerGroup');
    expect(job.cloudProvider).toBe('azure');
    expect(job.instance).toBe('myapp-dev-v086_3');
    expect(job.serverGroupName).toBe('myapp-dev-v086');
    expect(job.setMaxToNewDesired).toBe(true);
  });

  it('does not set setMaxToNewDesired when capacity min and max differ', async () => {
    spyOn(ServerGroupReader, 'getServerGroup').and.returnValue(
      Promise.resolve({ capacity: { min: 1, max: 4, desired: 4 } }) as any,
    );

    await AzureInstanceWriter.terminateInstanceAndShrinkServerGroup(instance, application);

    const job = (TaskExecutor.executeTask as jasmine.Spy).calls.mostRecent().args[0].job[0];
    expect(job.setMaxToNewDesired).toBe(false);
  });
});
