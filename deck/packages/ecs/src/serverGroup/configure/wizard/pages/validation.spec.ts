import { validateEcsNativeDeployment } from './validation';

describe('validateEcsNativeDeployment', () => {
  it('returns no errors when cloudProvider is plain ecs', () => {
    expect(
      validateEcsNativeDeployment({
        cloudProvider: 'ecs',
        deploymentStrategy: 'BLUE_GREEN',
      } as any),
    ).toEqual({});
  });

  it('returns no errors for native rolling deployment without optional fields', () => {
    expect(
      validateEcsNativeDeployment({
        cloudProvider: 'ecs-native',
        deploymentStrategy: 'ROLLING',
      } as any),
    ).toEqual({});
  });

  it('allows native blue/green when the ALB traffic-shift fields are complete', () => {
    const errors = validateEcsNativeDeployment({
      cloudProvider: 'ecs-native',
      deploymentStrategy: 'BLUE_GREEN',
      targetGroup: 'my-target-group',
      alternateTargetGroupArn: 'arn:alternate-target-group',
      productionListenerRule: 'arn:production-rule',
      testListenerRule: 'arn:test-rule',
      blueGreenRoleArn: 'arn:aws:iam::123456789012:role/ecsBlueGreenRole',
    } as any);

    expect(errors).toEqual({});
  });

  it('rejects a non-None Spinnaker strategy for native ECS', () => {
    const errors = validateEcsNativeDeployment({ cloudProvider: 'ecs-native', strategy: 'redblack' } as any);
    expect(errors.strategy).toBeTruthy();
  });

  it('validates native rolling deployment bounds', () => {
    const errors = validateEcsNativeDeployment({
      cloudProvider: 'ecs-native',
      deploymentStrategy: 'ROLLING',
      minimumHealthyPercent: -1,
      maximumPercent: 99,
    } as any);
    expect(errors.minimumHealthyPercent).toBeTruthy();
    expect(errors.maximumPercent).toBeTruthy();
  });

  it('allows a minimum healthy percent of 0, which ECS accepts', () => {
    const errors = validateEcsNativeDeployment({
      cloudProvider: 'ecs-native',
      deploymentStrategy: 'ROLLING',
      minimumHealthyPercent: 0,
      maximumPercent: 100,
    } as any);
    expect(errors).toEqual({});
  });

  it('rejects a minimum healthy percent above 100', () => {
    const errors = validateEcsNativeDeployment({
      cloudProvider: 'ecs-native',
      deploymentStrategy: 'ROLLING',
      minimumHealthyPercent: 101,
    } as any);
    expect(errors.minimumHealthyPercent).toBeTruthy();
  });

  it('requires maximum percent to be at least minimum healthy percent', () => {
    const errors = validateEcsNativeDeployment({
      cloudProvider: 'ecs-native',
      deploymentStrategy: 'ROLLING',
      minimumHealthyPercent: 80,
      maximumPercent: 70,
    } as any);
    expect(errors.maximumPercent).toBeTruthy();
  });
});
