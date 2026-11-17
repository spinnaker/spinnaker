import type { ITaskCommand } from '@spinnaker/core';
import { TaskExecutor } from '@spinnaker/core';

import { AmazonInstanceWriter } from './amazon.instance.write.service';
import type { IAmazonInstance } from '../domain';

describe('AmazonInstanceWriter', () => {
  describe('terminateInstanceInServerGroup', () => {
    it('terminates the instance through its auto scaling group', async () => {
      let task: ITaskCommand;
      vi.spyOn(TaskExecutor, 'executeTask').mockImplementation((command: ITaskCommand) => {
        task = command;
        return undefined;
      });
      const instance = {
        account: 'test',
        cloudProvider: 'aws',
        id: 'i-123',
        region: 'us-east-1',
        serverGroup: 'app-v000',
      } as IAmazonInstance;
      const application = { name: 'app' } as any;

      await AmazonInstanceWriter.terminateInstanceInServerGroup(instance, application);

      expect(task.application).toBe(application);
      expect(task.description).toBe('Terminate instance i-123 in app-v000');
      expect(task.job).toEqual([
        {
          type: 'terminateInstances',
          instanceIds: ['i-123'],
          serverGroupName: 'app-v000',
          region: 'us-east-1',
          credentials: 'test',
          cloudProvider: 'aws',
          terminateInAutoScalingGroup: true,
        },
      ]);
    });
  });
});
