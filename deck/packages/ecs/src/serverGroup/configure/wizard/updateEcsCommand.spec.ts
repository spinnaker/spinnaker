import { updateEcsCommand } from './EcsCloneServerGroupModal';
import type { IEcsServerGroupCommand } from '../serverGroupConfiguration.service';

describe('updateEcsCommand', () => {
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
  const buildCommand = (overrides: Partial<IEcsServerGroupCommand> = {}): IEcsServerGroupCommand =>
    ({ credentials: 'acct', region: 'us-west-2', viewState: {}, ...overrides } as any);

  it('forces the Spinnaker strategy to None when native deployment is enabled and remembers the previous one', () => {
    const { command } = updateEcsCommand(buildCommand({ strategy: 'redblack' }), 'cloudProvider', 'ecs-native');
    expect(command.cloudProvider).toBe('ecs-native');
    expect(command.strategy).toBe('none');
    expect(command.viewState.ecsNativePreviousStrategy).toBe('redblack');
  });

  it('restores the previous strategy and drops the native-only fields when native deployment is disabled', () => {
    const native = buildCommand({
      cloudProvider: 'ecs-native',
      strategy: 'none',
      enableDeploymentCircuitBreaker: true,
      useSourceCapacity: true,
      viewState: { ecsNativePreviousStrategy: 'highlander' } as any,
      ...nativeFields,
    } as any);

    const { command } = updateEcsCommand(native, 'cloudProvider', 'ecs');

    expect(command.cloudProvider).toBe('ecs');
    expect(command.strategy).toBe('highlander');
    Object.keys(nativeFields).forEach((field) => expect(command).not.toHaveProperty(field));
    // Classic ECS supports these, so they stay.
    expect(command.enableDeploymentCircuitBreaker).toBe(true);
    expect(command.useSourceCapacity).toBe(true);
  });

  it("falls back to the classic default strategy '' when nothing was remembered (e.g. a reopened pipeline stage)", () => {
    const { command } = updateEcsCommand(
      buildCommand({ cloudProvider: 'ecs-native', strategy: 'none' } as any),
      'cloudProvider',
      'ecs',
    );
    expect(command.strategy).toBe('');
  });

  it('clears the blue/green-only fields on an explicit switch to a rolling strategy, in any case', () => {
    const { command } = updateEcsCommand(
      buildCommand({ cloudProvider: 'ecs-native', ...nativeFields } as any),
      'deploymentStrategy',
      'rolling',
    );
    expect(command.deploymentStrategy).toBe('rolling');
    blueGreenOnly.forEach((field) => expect(command).not.toHaveProperty(field));
    expect(command.bakeTimeInMinutes).toBe(5);
  });

  it.each(['BLUE_GREEN', 'blue_green', '${ parameters.strategy }', ''])(
    'keeps the blue/green-only fields when the strategy becomes %p',
    (deploymentStrategy) => {
      const { command } = updateEcsCommand(
        buildCommand({ cloudProvider: 'ecs-native', ...nativeFields } as any),
        'deploymentStrategy',
        deploymentStrategy,
      );
      blueGreenOnly.forEach((field) => expect((command as any)[field]).toEqual((nativeFields as any)[field]));
    },
  );

  it('passes unrelated field changes through untouched', () => {
    const { command } = updateEcsCommand(buildCommand({ ...nativeFields } as any), 'stack', 'main');
    expect(command.stack).toBe('main');
    Object.keys(nativeFields).forEach((field) => expect((command as any)[field]).toEqual((nativeFields as any)[field]));
  });
});
