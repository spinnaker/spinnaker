import { SecurityGroupWriter } from './SecurityGroupWriter';
import { TaskExecutor } from '../task';

describe('SecurityGroupWriter', () => {
  it('copies the security group name on upsert', () => {
    const spy = spyOn(TaskExecutor, 'executeTask');
    SecurityGroupWriter.upsertSecurityGroup({ name: 'mySecurityGroupName' }, null, 'myDescription', {});

    expect(spy).toHaveBeenCalled();
    const job = spy.calls.mostRecent().args[0].job[0];
    expect(job).toEqual(jasmine.objectContaining({ securityGroupName: 'mySecurityGroupName' }));
  });
});
