import { TitusServerGroupTransformer } from './serverGroup.transformer';

describe('TitusServerGroupTransformer', () => {
  it('builds scaling policy templates with CloudWatch metric settings', () => {
    const serverGroup = { name: 'api-main-v001' };

    const stepPolicy = TitusServerGroupTransformer.constructNewStepScalingPolicyTemplate(serverGroup);
    expect(stepPolicy.adjustmentType).toBe('ChangeInCapacity');
    expect(stepPolicy.alarms[0].statistic).toBe('Average');
    expect(stepPolicy.alarms[0].dimensions).toEqual([{ name: 'AutoScalingGroupName', value: serverGroup.name }]);

    const targetTrackingPolicy = TitusServerGroupTransformer.constructNewTargetTrackingPolicyTemplate(serverGroup);
    expect(targetTrackingPolicy.targetTrackingConfiguration.customizedMetricSpecification.statistic).toBe('Average');
    expect(targetTrackingPolicy.targetTrackingConfiguration.customizedMetricSpecification.dimensions).toEqual([
      { name: 'AutoScalingGroupName', value: serverGroup.name },
    ]);
  });
});
