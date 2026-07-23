import { CloudProviderRegistry } from '../../../../cloudProvider/CloudProviderRegistry';
import type { IExecutionStage } from '../../../../domain';
import { UrlBuilder } from '../../../../navigation/UrlBuilder';
import { getDeployedServerGroups, getDeployWaitingMessages, hasDeployChanges } from './DeployExecutionDetails';

describe('DeployExecutionDetails helpers', () => {
  let originalGetValue: typeof CloudProviderRegistry.getValue;

  beforeAll(() => {
    originalGetValue = CloudProviderRegistry.getValue;
    CloudProviderRegistry.getValue = (cloudProvider: string) => cloudProvider === 'withScalingActivities';
  });

  beforeEach(() => {
    spyOn(UrlBuilder, 'buildFromMetadata').and.returnValue('#/server-group');
  });

  afterAll(() => {
    CloudProviderRegistry.getValue = originalGetValue;
  });

  const createStage = (context: any = {}, tasks: any[] = []): IExecutionStage => ({
    id: 'stage-id',
    name: 'Deploy',
    type: 'deploy',
    refId: '1',
    requisiteStageRefIds: [],
    status: 'RUNNING',
    startTime: 0,
    endTime: 0,
    tasks,
    context,
  });

  describe('deployment results', () => {
    it('sets empty list when no context or empty context', () => {
      expect(getDeployedServerGroups(createStage({ commits: [], jarDiffs: {} }), 'project')).toEqual([]);
      expect(getDeployedServerGroups(createStage(), 'project')).toEqual([]);
    });

    it('sets empty list when no kato.tasks or empty kato.tasks', () => {
      expect(getDeployedServerGroups(createStage({ 'kato.tasks': [] }), 'project')).toEqual([]);
      expect(getDeployedServerGroups(createStage({ 'kato.tasks': [{}] }), 'project')).toEqual([]);
      expect(getDeployedServerGroups(createStage({ 'kato.tasks': [{ resultObjects: [] }] }), 'project')).toEqual([]);
      expect(getDeployedServerGroups(createStage({ 'kato.tasks': [{ resultObjects: [{}] }] }), 'project')).toEqual([]);
    });

    it('sets deployed when serverGroupNameByRegion supplies values', () => {
      const deployed = getDeployedServerGroups(
        createStage({
          application: 'fnord',
          account: 'test',
          cloudProvider: 'aws',
          'kato.tasks': [
            {
              resultObjects: [
                {
                  serverGroupNameByRegion: { 'us-west-1': 'deployedWest', 'us-east-1': 'deployedEast' },
                },
              ],
            },
          ],
        }),
        'project',
      );

      expect(deployed.length).toBe(2);
      expect(deployed[0].serverGroup).toBe('deployedWest');
      expect(deployed[1].serverGroup).toBe('deployedEast');
    });
  });

  describe('changes section visibility', () => {
    it('only shows changes when commits or jar diffs exist', () => {
      expect(hasDeployChanges(createStage({ commits: [], jarDiffs: {} }))).toBe(false);
      expect(hasDeployChanges(createStage({ commits: [{ sha: 'abc' }], jarDiffs: {} }))).toBe(true);
      expect(hasDeployChanges(createStage({ commits: [], jarDiffs: { library: ['changed'] } }))).toBe(true);
    });
  });

  describe('running warnings', () => {
    const createRunningStage = () =>
      createStage(
        {
          cloudProvider: 'aws',
          application: 'fnord',
          account: 'test',
          'kato.tasks': [{ resultObjects: [{ serverGroupNameByRegion: { 'us-west-1': 'deployedWest' } }] }],
        },
        [
          { name: 'forceCacheRefresh', status: 'RUNNING' },
          { name: 'waitForUpInstances', status: 'NOT_STARTED' },
        ],
      );

    it('sets waitingForUpInstances flag when waitForUpInstances is running and lastCapacityCheck reported', () => {
      const stage = createRunningStage();
      const deployed = getDeployedServerGroups(stage, 'project');

      expect(getDeployWaitingMessages(stage, {} as any, deployed).waitingForUpInstances).toBe(false);

      stage.tasks[0].status = 'COMPLETED';
      stage.tasks[1].status = 'RUNNING';
      expect(getDeployWaitingMessages(stage, {} as any, deployed).waitingForUpInstances).toBe(false);

      stage.context.lastCapacityCheck = {};
      expect(getDeployWaitingMessages(stage, {} as any, deployed).waitingForUpInstances).toBe(true);
    });

    it('sets showScalingActivitiesLink if configured for cloud provider and five minutes have passed', () => {
      const stage = createRunningStage();
      const deployed = getDeployedServerGroups(stage, 'project');
      stage.context.lastCapacityCheck = {
        up: 1,
        down: 0,
        outOfService: 0,
        unknown: 0,
        succeeded: 0,
        failed: 0,
      };
      stage.context.capacity = { desired: 2 };
      stage.tasks[0].status = 'COMPLETED';
      stage.tasks[1].status = 'RUNNING';

      expect(getDeployWaitingMessages(stage, {} as any, deployed).showScalingActivitiesLink).toBe(false);

      stage.context.cloudProvider = 'withScalingActivities';
      expect(getDeployWaitingMessages(stage, {} as any, deployed).showScalingActivitiesLink).toBe(false);

      stage.tasks[1].runningTimeInMs = 5 * 60 * 1000 + 1;
      expect(getDeployWaitingMessages(stage, {} as any, deployed).showScalingActivitiesLink).toBe(true);
    });

    it('sets showPlatformHealthOverrideMessage after five minutes if unknown status detected and platformHealthOverride not configured', () => {
      const stage = createRunningStage();
      const deployed = getDeployedServerGroups(stage, 'project');
      stage.context.lastCapacityCheck = {
        up: 0,
        down: 0,
        outOfService: 0,
        unknown: 1,
        succeeded: 0,
        failed: 0,
      };
      stage.context.capacity = { desired: 1 };
      stage.tasks[0].status = 'COMPLETED';
      stage.tasks[1].status = 'RUNNING';
      stage.tasks[1].runningTimeInMs = 5 * 60 * 1000 + 1;

      expect(
        getDeployWaitingMessages(stage, { attributes: {} } as any, deployed).showPlatformHealthOverrideMessage,
      ).toBe(true);
      expect(
        getDeployWaitingMessages(stage, { attributes: { platformHealthOverride: true } } as any, deployed)
          .showPlatformHealthOverrideMessage,
      ).toBe(false);

      stage.context.interestingHealthProviderNames = ['Amazon'];
      expect(
        getDeployWaitingMessages(stage, { attributes: {} } as any, deployed).showPlatformHealthOverrideMessage,
      ).toBe(false);
    });
  });
});
