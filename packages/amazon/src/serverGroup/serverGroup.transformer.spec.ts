import { mock, IQService, IRootScopeService, IScope } from 'angular';

import { AWS_SERVER_GROUP_TRANSFORMER, AwsServerGroupTransformer } from './serverGroup.transformer';
import { IScalingPolicyAlarmView, IAmazonServerGroup, IStepAdjustment } from '../domain';
import { VpcReader } from '../vpc/VpcReader';

describe('awsServerGroupTransformer', () => {
  let transformer: AwsServerGroupTransformer, $q: IQService, $scope: IScope;

  beforeEach(mock.module(AWS_SERVER_GROUP_TRANSFORMER));

  beforeEach(
    mock.inject(
      (_awsServerGroupTransformer_: AwsServerGroupTransformer, _$q_: IQService, $rootScope: IRootScopeService) => {
        transformer = _awsServerGroupTransformer_;
        $q = _$q_;
        $scope = $rootScope.$new();
      },
    ),
  );

  describe('normalize server group', () => {
    beforeEach(() => {
      spyOn(VpcReader, 'listVpcs').and.returnValue(
        $q.when([{ account: 'test', region: 'us-east-1', id: 'vpc-1', name: 'main' } as any]),
      );
    });

    it('adds vpc name to server group', () => {
      const serverGroup = {
        account: 'test',
        region: 'us-east-1',
        vpcId: 'vpc-1',
        instances: [],
      } as IAmazonServerGroup;
      transformer.normalizeServerGroup(serverGroup);
      $scope.$digest();
      expect(serverGroup.vpcName).toBe('main');
    });

    it('adds empty vpc name when no vpcId found on server group', () => {
      const serverGroup = {
        account: 'test',
        region: 'us-east-1',
        instances: [],
      } as IAmazonServerGroup;
      transformer.normalizeServerGroup(serverGroup);
      $scope.$digest();
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
        this.test = (steps: IStepAdjustment[], expected: any[]) => {
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
        this.test(
          [
            { scalingAdjustment: 10, metricIntervalLowerBound: 3 },
            { scalingAdjustment: 0, metricIntervalLowerBound: 5 },
            { scalingAdjustment: -5, metricIntervalLowerBound: 1 },
          ],
          [-5, 10, 0],
        );
      });

      it('reverse sorts step adjustments by lower bound when some do not have an upper bound defined', function () {
        this.test(
          [
            { id: 1, scalingAdjustment: 10, metricIntervalLowerBound: 3, metricIntervalUpperBound: 5 },
            { id: 2, scalingAdjustment: 0, metricIntervalLowerBound: 5 },
            { id: 3, scalingAdjustment: -5, metricIntervalLowerBound: 1, metricIntervalUpperBound: 3 },
          ],
          [-5, 10, 0],
        );
      });

      it('reverse sorts step adjustments by upper bound when all have an upper bound defined', function () {
        this.test(
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
