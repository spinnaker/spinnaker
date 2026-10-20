import type { IAmazonServerGroup, IScalingPolicyAlarmView, IStepAdjustment } from '@spinnaker/amazon';
import { VpcReader } from '@spinnaker/amazon';

import { EcsServerGroupTransformer } from './serverGroup.transformer';

const testContext: any = {};

describe('ecsServerGroupTransformer', () => {
  let transformer: EcsServerGroupTransformer;

  beforeEach(() => {
    transformer = new EcsServerGroupTransformer();
  });

  describe('normalize server group', () => {
    beforeEach(() => {
      vi.spyOn(VpcReader, 'listVpcs').mockReturnValue(
        Promise.resolve([{ account: 'test', region: 'us-east-1', id: 'vpc-1', name: 'main' } as any]),
      );
    });

    it('adds vpc name to server group', async () => {
      const serverGroup = {
        account: 'test',
        region: 'us-east-1',
        vpcId: 'vpc-1',
        instances: [],
      } as IAmazonServerGroup;
      await transformer.normalizeServerGroup(serverGroup);
      expect(serverGroup.vpcName).toBe('main');
    });

    it('adds empty vpc name when no vpcId found on server group', async () => {
      const serverGroup = {
        account: 'test',
        region: 'us-east-1',
        instances: [],
      } as IAmazonServerGroup;
      await transformer.normalizeServerGroup(serverGroup);
      expect(serverGroup.vpcName).toBe('');
    });
  });

  describe('command transforms', () => {
    it('sets subnetType property to empty string when null', () => {
      const command: any = {
        viewState: {
          mode: 'create',
          useAllImageSelection: true,
          allImageSelection: 'something-packagebase',
        },
        subnetType: null,
        application: { name: 'theApp' },
      };

      let transformed = transformer.convertServerGroupCommandToDeployConfiguration(command);
      expect(transformed.subnetType).toBe('');

      command.subnetType = 'internal';
      transformed = transformer.convertServerGroupCommandToDeployConfiguration(command);
      expect(transformed.subnetType).toBe('internal');
    });

    it('defaults cloudProvider to ecs', () => {
      const command: any = { viewState: { mode: 'create' }, application: { name: 'theApp' } };
      const transformed = transformer.convertServerGroupCommandToDeployConfiguration(command);
      expect(transformed.cloudProvider).toBe('ecs');
    });

    it('preserves cloudProvider ecs-native when the native deployment toggle is set', () => {
      const command: any = {
        viewState: { mode: 'create' },
        application: { name: 'theApp' },
        cloudProvider: 'ecs-native',
      };
      const transformed = transformer.convertServerGroupCommandToDeployConfiguration(command);
      expect(transformed.cloudProvider).toBe('ecs-native');
    });

    const nativeFields = {
      deploymentStrategy: 'BLUE_GREEN',
      deploymentCircuitBreakerRollback: true,
      alarmNames: ['alarm'],
      enableDeploymentAlarms: true,
      deploymentAlarmsRollback: true,
      minimumHealthyPercent: 50,
      maximumPercent: 200,
      bakeTimeInMinutes: 5,
      lifecyclePauseStage: 'POST_TEST_TRAFFIC_SHIFT',
      lifecyclePauseTimeoutMinutes: 30,
      lifecyclePauseTimeoutAction: 'ROLLBACK',
      alternateTargetGroupArn: 'arn:alt',
      productionListenerRule: 'arn:prod',
      testListenerRule: 'arn:test',
      blueGreenRoleArn: 'arn:role',
    };
    const blueGreenOnly = [
      'lifecyclePauseStage',
      'lifecyclePauseTimeoutMinutes',
      'lifecyclePauseTimeoutAction',
      'alternateTargetGroupArn',
      'productionListenerRule',
      'testListenerRule',
      'blueGreenRoleArn',
    ];

    it('strips native-only fields from a classic ecs payload but keeps fields classic ecs supports', () => {
      const command: any = {
        viewState: { mode: 'create' },
        application: { name: 'theApp' },
        cloudProvider: 'ecs',
        enableDeploymentCircuitBreaker: true,
        useSourceCapacity: true,
        ...nativeFields,
      };
      const transformed = transformer.convertServerGroupCommandToDeployConfiguration(command);
      Object.keys(nativeFields).forEach((field) => expect(transformed).not.toHaveProperty(field));
      expect(transformed.enableDeploymentCircuitBreaker).toBe(true);
      expect(transformed.useSourceCapacity).toBe(true);
    });

    it('strips blue/green-only fields from a native payload whose strategy is rolling (any case)', () => {
      const command: any = {
        viewState: { mode: 'create' },
        application: { name: 'theApp' },
        cloudProvider: 'ecs-native',
        ...nativeFields,
        deploymentStrategy: 'rolling',
      };
      const transformed = transformer.convertServerGroupCommandToDeployConfiguration(command);
      blueGreenOnly.forEach((field) => expect(transformed).not.toHaveProperty(field));
      expect(transformed.bakeTimeInMinutes).toBe(5);
      expect(transformed.deploymentStrategy).toBe('rolling');
    });

    it.each(['BLUE_GREEN', 'blue_green', '${ parameters.strategy }', undefined])(
      'keeps blue/green-only fields on a native payload when the strategy is %p',
      (deploymentStrategy) => {
        const command: any = {
          viewState: { mode: 'create' },
          application: { name: 'theApp' },
          cloudProvider: 'ecs-native',
          ...nativeFields,
          deploymentStrategy,
        };
        const transformed = transformer.convertServerGroupCommandToDeployConfiguration(command);
        blueGreenOnly.forEach((field) => expect(transformed[field]).toEqual((nativeFields as any)[field]));
      },
    );
  });

  describe('normalize server group details', () => {
    it('adds appropriate comparator to alarm', () => {
      const serverGroup = {
        scalingPolicies: [
          {
            alarms: [
              { comparisonOperator: 'LessThanThreshold' },
              { comparisonOperator: 'GreaterThanThreshold' },
              { comparisonOperator: 'LessThanOrEqualToThreshold' },
              { comparisonOperator: 'GreaterThanOrEqualToThreshold' },
              { comparisonOperator: 'WhatIsThis' },
            ],
          },
        ],
      } as IAmazonServerGroup;
      transformer.normalizeServerGroupDetails(serverGroup);
      const alarms = serverGroup.scalingPolicies[0].alarms as IScalingPolicyAlarmView[];
      expect(alarms.map((a) => a.comparator)).toEqual(['&lt;', '&gt;', '&le;', '&ge;', undefined]);
    });

    it('adds operator, absAdjustment to simple policies', () => {
      const serverGroup = {
        scalingPolicies: [{ scalingAdjustment: 10 }, { scalingAdjustment: 0 }, { scalingAdjustment: -5 }],
      } as IAmazonServerGroup;
      const transformed = transformer.normalizeServerGroupDetails(serverGroup);
      const policies = transformed.scalingPolicies;
      expect(policies.map((a) => a.absAdjustment)).toEqual([10, 0, 5]);
      expect(policies.map((a) => a.operator)).toEqual(['increase', 'increase', 'decrease']);
    });

    it('adds operator, absAdjustment to step policies', () => {
      const serverGroup = {
        scalingPolicies: [
          {
            stepAdjustments: [
              { scalingAdjustment: 10, metricIntervalLowerBound: 0 },
              { scalingAdjustment: 0, metricIntervalLowerBound: 6 },
              { scalingAdjustment: -5, metricIntervalLowerBound: 11 },
            ],
          },
        ],
      } as IAmazonServerGroup;
      const transformed = transformer.normalizeServerGroupDetails(serverGroup);
      const steps = transformed.scalingPolicies[0].stepAdjustments;
      expect(steps.map((a) => a.absAdjustment)).toEqual([10, 0, 5]);
      expect(steps.map((a) => a.operator)).toEqual(['increase', 'increase', 'decrease']);
    });

    describe('sorting step adjustments', () => {
      beforeEach(function () {
        testContext.test = (steps: IStepAdjustment[], expected: any[]) => {
          const serverGroup = {
            scalingPolicies: [
              {
                stepAdjustments: steps,
              },
            ],
          } as IAmazonServerGroup;
          transformer.normalizeServerGroupDetails(serverGroup);
          const check = serverGroup.scalingPolicies[0].stepAdjustments;
          expect(check.map((s) => s.scalingAdjustment)).toEqual(expected);
        };
      });

      it('reverse sorts step adjustments by lower bound when none have an upper bound defined', function () {
        testContext.test(
          [
            { scalingAdjustment: 10, metricIntervalLowerBound: 3 },
            { scalingAdjustment: 0, metricIntervalLowerBound: 5 },
            { scalingAdjustment: -5, metricIntervalLowerBound: 1 },
          ],
          [-5, 10, 0],
        );
      });

      it('reverse sorts step adjustments by lower bound when some do not have an upper bound defined', function () {
        testContext.test(
          [
            { id: 1, scalingAdjustment: 10, metricIntervalLowerBound: 3, metricIntervalUpperBound: 5 },
            { id: 2, scalingAdjustment: 0, metricIntervalLowerBound: 5 },
            { id: 3, scalingAdjustment: -5, metricIntervalLowerBound: 1, metricIntervalUpperBound: 3 },
          ],
          [-5, 10, 0],
        );
      });
      it('verify float adjustments work within the range', function () {
        testContext.test(
          [
            { id: 1, scalingAdjustment: 10, metricIntervalLowerBound: 3.5, metricIntervalUpperBound: 5.5 },
            { id: 2, scalingAdjustment: 0, metricIntervalLowerBound: 5.5 },
            { id: 3, scalingAdjustment: -5, metricIntervalLowerBound: 1.2, metricIntervalUpperBound: 3.5 },
          ],
          [-5, 10, 0],
        );
      });
      it('reverse sorts step adjustments by upper bound when all have an upper bound defined', function () {
        testContext.test(
          [
            { id: 1, scalingAdjustment: 10, metricIntervalLowerBound: 3, metricIntervalUpperBound: 5 },
            { id: 2, scalingAdjustment: 0, metricIntervalLowerBound: 5, metricIntervalUpperBound: 9 },
            { id: 3, scalingAdjustment: -5, metricIntervalLowerBound: 1, metricIntervalUpperBound: 0 },
          ],
          [0, 10, -5],
        );
      });
    });
  });
});
