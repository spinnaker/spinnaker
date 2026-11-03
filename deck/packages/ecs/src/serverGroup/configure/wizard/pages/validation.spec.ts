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

  const completeBlueGreenFields = {
    alternateTargetGroupArn: 'arn:alternate-target-group',
    productionListenerRule: 'arn:production-rule',
    testListenerRule: 'arn:test-rule',
    blueGreenRoleArn: 'arn:aws:iam::123456789012:role/ecsBlueGreenRole',
  };
  const blueGreenFieldNames = Object.keys(completeBlueGreenFields);

  it('requires all four ALB traffic-shift fields for a load-balanced blue/green deploy', () => {
    const errors = validateEcsNativeDeployment({
      cloudProvider: 'ecs-native',
      deploymentStrategy: 'BLUE_GREEN',
      targetGroup: 'my-target-group',
    } as any);
    expect(Object.keys(errors).sort()).toEqual(blueGreenFieldNames.sort());
    expect(errors.alternateTargetGroupArn).toContain('requires all four fields');
  });

  it('flags the missing fields of a partial ALB traffic-shift set on a blue/green deploy', () => {
    const errors = validateEcsNativeDeployment({
      cloudProvider: 'ecs-native',
      deploymentStrategy: 'BLUE_GREEN',
      alternateTargetGroupArn: 'arn:alternate-target-group',
    } as any);
    expect(Object.keys(errors).sort()).toEqual(['blueGreenRoleArn', 'productionListenerRule', 'testListenerRule']);
    expect(errors.productionListenerRule).toContain('all be set together');
  });

  it('accepts a lowercase blue_green strategy with complete traffic-shift fields, like clouddriver', () => {
    const errors = validateEcsNativeDeployment({
      cloudProvider: 'ecs-native',
      deploymentStrategy: 'blue_green',
      targetGroup: 'my-target-group',
      ...completeBlueGreenFields,
    } as any);
    expect(errors).toEqual({});
  });

  it('does not validate blue/green-only fields the wizard no longer shows after switching to ROLLING', () => {
    // A partial leftover set must not block saving on hidden inputs; updateEcsCommand clears these
    // on the strategy switch, and clouddriver remains the backstop for anything that slips through.
    const errors = validateEcsNativeDeployment({
      cloudProvider: 'ecs-native',
      deploymentStrategy: 'ROLLING',
      targetGroup: 'my-target-group',
      alternateTargetGroupArn: 'arn:alternate-target-group',
      lifecyclePauseStage: 'POST_TEST_TRAFFIC_SHIFT',
    } as any);
    expect(errors).toEqual({});
  });

  it.each([
    [undefined, false],
    [0, false],
    [15, false],
    [-1, true],
    [1.5, true],
    [NaN, true],
  ])('bake time %p is invalid: %p', (bakeTimeInMinutes, invalid) => {
    const errors = validateEcsNativeDeployment({
      cloudProvider: 'ecs-native',
      deploymentStrategy: 'ROLLING',
      bakeTimeInMinutes,
    } as any);
    expect(Boolean(errors.bakeTimeInMinutes)).toBe(invalid);
  });

  it.each([
    [undefined, true],
    [0, true],
    [1.5, true],
    [1, false],
    [30, false],
  ])('pause timeout %p with a pause stage configured is invalid: %p', (lifecyclePauseTimeoutMinutes, invalid) => {
    const errors = validateEcsNativeDeployment({
      cloudProvider: 'ecs-native',
      deploymentStrategy: 'BLUE_GREEN',
      lifecyclePauseStage: 'POST_TEST_TRAFFIC_SHIFT',
      lifecyclePauseTimeoutMinutes,
    } as any);
    expect(Boolean(errors.lifecyclePauseTimeoutMinutes)).toBe(invalid);
  });

  it('does not require a pause timeout when no pause stage is configured', () => {
    const errors = validateEcsNativeDeployment({
      cloudProvider: 'ecs-native',
      deploymentStrategy: 'BLUE_GREEN',
    } as any);
    expect(errors).toEqual({});
  });

  it('leaves classic ecs commands alone even when native fields are present', () => {
    const errors = validateEcsNativeDeployment({
      cloudProvider: 'ecs',
      bakeTimeInMinutes: -1,
      lifecyclePauseStage: 'POST_TEST_TRAFFIC_SHIFT',
      deploymentStrategy: 'BLUE_GREEN',
      targetGroup: 'tg',
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
