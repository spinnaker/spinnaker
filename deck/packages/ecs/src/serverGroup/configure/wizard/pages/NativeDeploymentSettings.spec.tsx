import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import type { IEcsServerGroupCommand } from '../../serverGroupConfiguration.service';
import { NativeDeploymentSettings } from './NativeDeploymentSettings';

describe('NativeDeploymentSettings', () => {
  const buildCommand = (overrides: Partial<IEcsServerGroupCommand> = {}): IEcsServerGroupCommand =>
    ({
      backingData: { filtered: { metricAlarms: [] } },
      ...overrides,
    } as any);

  const renderSettings = (command: IEcsServerGroupCommand, onFieldChange: (...args: any[]) => void = vi.fn()) =>
    render(
      <NativeDeploymentSettings
        application={null as any}
        command={command}
        configureCommand={() => Promise.resolve()}
        onFieldChange={onFieldChange}
      />,
    );

  const byTestId = (container: HTMLElement, testId: string) =>
    container.querySelector(`[data-test-id="${testId}"]`) as HTMLElement;

  it('only shows the toggle when native deployment is off', () => {
    const { container } = renderSettings(buildCommand());
    expect(screen.getByRole('checkbox', { name: 'Use native ECS deployment' })).not.toBeChecked();
    expect(byTestId(container, 'NativeDeployment.deploymentStrategy')).toBeNull();
  });

  it('toggling the checkbox sets cloudProvider to ecs-native', () => {
    const onFieldChange = vi.fn();
    renderSettings(buildCommand(), onFieldChange);
    fireEvent.click(screen.getByRole('checkbox', { name: 'Use native ECS deployment' }));
    expect(onFieldChange).toHaveBeenCalledWith('cloudProvider', 'ecs-native');
  });

  it('unchecking the checkbox sets cloudProvider back to ecs', () => {
    const onFieldChange = vi.fn();
    renderSettings(buildCommand({ cloudProvider: 'ecs-native' }), onFieldChange);
    fireEvent.click(screen.getByRole('checkbox', { name: 'Use native ECS deployment' }));
    expect(onFieldChange).toHaveBeenCalledWith('cloudProvider', 'ecs');
  });

  it('shows the native fields, defaulting to ROLLING with bake time but no ALB traffic-shift fields, once enabled', () => {
    const { container } = renderSettings(buildCommand({ cloudProvider: 'ecs-native' }));
    // ecs-native is always in-place now; the "Redeploy in place" toggle was removed.
    expect(byTestId(container, 'NativeDeployment.inPlaceUpdate')).toBeNull();
    expect(screen.getByRole('combobox', { name: 'Native ECS deployment strategy' })).toHaveValue('ROLLING');
    // Bake time is valid for the ECS deployment controller regardless of strategy, so it shows for ROLLING too.
    expect(screen.getByLabelText('Bake time in minutes')).toBeInTheDocument();
    // The ALB traffic-shift fields remain blue/green-only.
    expect(screen.queryByLabelText('Alternate target group ARN')).not.toBeInTheDocument();
  });

  it('reveals the ALB traffic-shift fields once BLUE_GREEN is selected, with bake time still shown', () => {
    renderSettings(buildCommand({ cloudProvider: 'ecs-native', deploymentStrategy: 'BLUE_GREEN' }));
    expect(screen.getByLabelText('Bake time in minutes')).toBeInTheDocument();
    expect(screen.getByLabelText('Alternate target group ARN')).toBeInTheDocument();
    expect(screen.getByLabelText('Production listener rule ARN')).toBeInTheDocument();
    expect(screen.getByLabelText('Test listener rule ARN')).toBeInTheDocument();
    expect(screen.getByLabelText('Blue/Green IAM role ARN')).toBeInTheDocument();
    const strategy = screen.getByRole('combobox', { name: 'Native ECS deployment strategy' });
    expect(strategy).toHaveValue('BLUE_GREEN');
    expect(screen.getByRole('option', { name: 'Blue/Green' })).toBeEnabled();
  });

  it('disables the circuit-breaker rollback checkbox until the circuit breaker itself is enabled', () => {
    const rendered = renderSettings(buildCommand({ cloudProvider: 'ecs-native' }));
    const rollback = () => screen.getByRole('checkbox', { name: 'Automatically roll back a failed deployment' });
    expect(rollback()).toBeDisabled();
    rendered.rerender(
      <NativeDeploymentSettings
        application={null as any}
        command={buildCommand({ cloudProvider: 'ecs-native', enableDeploymentCircuitBreaker: true })}
        configureCommand={() => Promise.resolve()}
        onFieldChange={vi.fn()}
      />,
    );
    expect(rollback()).toBeEnabled();
  });

  it('adds an alarm name when its checkbox is checked, and removes it when unchecked', () => {
    const onFieldChange = vi.fn();
    const rendered = renderSettings(
      buildCommand({
        cloudProvider: 'ecs-native',
        backingData: { filtered: { metricAlarms: [{ alarmName: 'myapp-high-error-rate' }] } } as any,
      }),
      onFieldChange,
    );
    fireEvent.click(screen.getByRole('checkbox', { name: 'myapp-high-error-rate' }));
    expect(onFieldChange).toHaveBeenCalledWith('alarmNames', ['myapp-high-error-rate']);

    onFieldChange.mockClear();
    rendered.rerender(
      <NativeDeploymentSettings
        application={null as any}
        command={buildCommand({
          cloudProvider: 'ecs-native',
          alarmNames: ['myapp-high-error-rate'],
          backingData: { filtered: { metricAlarms: [{ alarmName: 'myapp-high-error-rate' }] } } as any,
        })}
        configureCommand={() => Promise.resolve()}
        onFieldChange={onFieldChange}
      />,
    );
    const alarm = screen.getByRole('checkbox', { name: 'myapp-high-error-rate' });
    expect(alarm).toBeChecked();
    fireEvent.click(alarm);
    expect(onFieldChange).toHaveBeenCalledWith('alarmNames', []);
  });

  it('only reports the cloudProvider change when toggling; the command update owns the strategy and field cleanup', () => {
    // Forcing strategy=None on the way in, and restoring it plus dropping the native-only fields on
    // the way out, is done in updateEcsCommand (see its spec) so it also applies to formik's values.
    const onFieldChange = vi.fn();
    renderSettings(buildCommand({ strategy: 'redblack' }), onFieldChange);
    fireEvent.click(screen.getByRole('checkbox', { name: 'Use native ECS deployment' }));
    expect(onFieldChange).toHaveBeenCalledTimes(1);
    expect(onFieldChange).toHaveBeenCalledWith('cloudProvider', 'ecs-native');
  });

  it('treats a lowercase blue_green strategy like BLUE_GREEN, as clouddriver does', () => {
    renderSettings(buildCommand({ cloudProvider: 'ecs-native', deploymentStrategy: 'blue_green' }));
    expect(screen.getByLabelText('Alternate target group ARN')).toBeInTheDocument();
  });

  it('shows minimum and maximum healthy deployment bounds', () => {
    renderSettings(buildCommand({ cloudProvider: 'ecs-native' }));
    expect(screen.getByLabelText('Minimum healthy percent')).toBeInTheDocument();
    expect(screen.getByLabelText('Maximum percent')).toBeInTheDocument();
  });
});
