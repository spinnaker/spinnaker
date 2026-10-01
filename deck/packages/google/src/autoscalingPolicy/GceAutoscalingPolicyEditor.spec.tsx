import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';

import { GcePredictiveMethod } from './IGceAutoscalingPolicy';
import { GceAutoscalingPolicyEditor } from './GceAutoscalingPolicyEditor';

describe('GceAutoscalingPolicyEditor', () => {
  it('renders zero values without replacing them with blanks', () => {
    render(
      <GceAutoscalingPolicyEditor
        policy={{
          minNumReplicas: 0,
          cpuUtilization: { utilizationTarget: 0, predictiveMethod: GcePredictiveMethod.NONE },
          scaleInControl: { maxScaledInReplicas: { percent: 0 }, timeWindowSec: 60 },
        }}
        onChange={() => undefined}
        predictiveAutoscalingEnabled={true}
      />,
    );

    expect(screen.getByTestId('minimum-replicas')).toHaveValue(0);
    expect(screen.getByTestId('cpu-target')).toHaveValue(0);
    expect(screen.getByTestId('scale-in-maximum')).toHaveValue(0);
    expect(screen.getByTestId('predictive-autoscaling')).not.toBeChecked();
  });

  it('keeps the predictive setting behind its feature gate and writes NONE when disabled', () => {
    const onChange = vi.fn();
    const policy = {
      cpuUtilization: { utilizationTarget: 0.5, predictiveMethod: GcePredictiveMethod.STANDARD },
    };
    const hidden = render(
      <GceAutoscalingPolicyEditor policy={policy} onChange={onChange} predictiveAutoscalingEnabled={false} />,
    );
    expect(hidden.queryByTestId('predictive-autoscaling')).not.toBeInTheDocument();
    hidden.unmount();

    const visible = render(
      <GceAutoscalingPolicyEditor policy={policy} onChange={onChange} predictiveAutoscalingEnabled={true} />,
    );
    fireEvent.click(visible.getByTestId('predictive-autoscaling'));

    expect(onChange).toHaveBeenCalledWith({
      cpuUtilization: { utilizationTarget: 0.5, predictiveMethod: GcePredictiveMethod.NONE },
    });
  });

  it('edits CPU, HTTP load-balancing, and custom metrics as controlled values', () => {
    const onChange = vi.fn();
    const policy = {
      cpuUtilization: { utilizationTarget: 0.5 },
      loadBalancingUtilization: { utilizationTarget: 0.6 },
      customMetricUtilizations: [{ metric: 'custom.googleapis.com/queue', utilizationTarget: 3 }],
    };
    render(<GceAutoscalingPolicyEditor policy={policy} onChange={onChange} />);

    fireEvent.change(screen.getByTestId('cpu-target'), { target: { value: '0' } });
    expect(onChange).toHaveBeenCalledWith({ ...policy, cpuUtilization: { utilizationTarget: 0 } });

    fireEvent.change(screen.getByTestId('http-lb-target'), { target: { value: '75' } });
    expect(onChange).toHaveBeenCalledWith({ ...policy, loadBalancingUtilization: { utilizationTarget: 0.75 } });

    fireEvent.change(screen.getByTestId('custom-metric-target-0'), { target: { value: '0' } });
    expect(onChange).toHaveBeenCalledWith({
      ...policy,
      customMetricUtilizations: [{ metric: 'custom.googleapis.com/queue', utilizationTarget: 0 }],
    });
  });

  it('clears group-only scaling fields when a custom metric changes to per-instance scope', () => {
    const onChange = vi.fn();
    const policy = {
      customMetricUtilizations: [
        {
          metric: 'custom.googleapis.com/queue',
          metricExportScope: 'SINGLE_TIME_SERIES_PER_GROUP' as const,
          scalingpolicy: 'SINGLE_INSTANCE_ASSIGNMENT' as const,
          singleInstanceAssignment: 3,
        },
      ],
    };
    render(<GceAutoscalingPolicyEditor policy={policy} onChange={onChange} />);

    fireEvent.change(screen.getByLabelText('Metric export scope'), {
      target: { value: 'TIME_SERIES_PER_INSTANCE' },
    });

    expect(onChange).toHaveBeenCalledWith({
      customMetricUtilizations: [
        {
          metric: 'custom.googleapis.com/queue',
          metricExportScope: 'TIME_SERIES_PER_INSTANCE',
        },
      ],
    });
  });

  it('defaults to utilization target when a custom metric changes to group scope', () => {
    const onChange = vi.fn();
    const policy = {
      customMetricUtilizations: [
        {
          metric: 'custom.googleapis.com/queue',
          metricExportScope: 'TIME_SERIES_PER_INSTANCE' as const,
          utilizationTarget: 3,
          utilizationTargetType: 'GAUGE' as const,
        },
      ],
    };
    render(<GceAutoscalingPolicyEditor policy={policy} onChange={onChange} />);

    fireEvent.change(screen.getByLabelText('Metric export scope'), {
      target: { value: 'SINGLE_TIME_SERIES_PER_GROUP' },
    });

    expect(onChange).toHaveBeenCalledWith({
      customMetricUtilizations: [
        {
          metric: 'custom.googleapis.com/queue',
          metricExportScope: 'SINGLE_TIME_SERIES_PER_GROUP',
          scalingpolicy: 'UTILIZATION_TARGET',
          utilizationTarget: 3,
          utilizationTargetType: 'GAUGE',
        },
      ],
    });
  });

  it('clears single-instance assignment when group scaling switches to utilization target', () => {
    const onChange = vi.fn();
    const policy = {
      customMetricUtilizations: [
        {
          metric: 'custom.googleapis.com/queue',
          metricExportScope: 'SINGLE_TIME_SERIES_PER_GROUP' as const,
          scalingpolicy: 'SINGLE_INSTANCE_ASSIGNMENT' as const,
          singleInstanceAssignment: 3,
        },
      ],
    };
    render(<GceAutoscalingPolicyEditor policy={policy} onChange={onChange} />);

    fireEvent.change(screen.getByLabelText('Scaling policy'), { target: { value: 'UTILIZATION_TARGET' } });

    expect(onChange).toHaveBeenCalledWith({
      customMetricUtilizations: [
        {
          metric: 'custom.googleapis.com/queue',
          metricExportScope: 'SINGLE_TIME_SERIES_PER_GROUP',
          scalingpolicy: 'UTILIZATION_TARGET',
        },
      ],
    });
  });

  it('clears utilization fields when group scaling switches to single-instance assignment', () => {
    const onChange = vi.fn();
    const policy = {
      customMetricUtilizations: [
        {
          metric: 'custom.googleapis.com/queue',
          metricExportScope: 'SINGLE_TIME_SERIES_PER_GROUP' as const,
          scalingpolicy: 'UTILIZATION_TARGET' as const,
          utilizationTarget: 5,
          utilizationTargetType: 'GAUGE' as const,
        },
      ],
    };
    render(<GceAutoscalingPolicyEditor policy={policy} onChange={onChange} />);

    fireEvent.change(screen.getByLabelText('Scaling policy'), {
      target: { value: 'SINGLE_INSTANCE_ASSIGNMENT' },
    });

    expect(onChange).toHaveBeenCalledWith({
      customMetricUtilizations: [
        {
          metric: 'custom.googleapis.com/queue',
          metricExportScope: 'SINGLE_TIME_SERIES_PER_GROUP',
          scalingpolicy: 'SINGLE_INSTANCE_ASSIGNMENT',
        },
      ],
    });
  });

  it('returns to the add action after a CPU metric is marked for deletion', () => {
    const onChange = vi.fn();
    const rendered = render(
      <GceAutoscalingPolicyEditor policy={{ cpuUtilization: { utilizationTarget: 0.5 } }} onChange={onChange} />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Delete CPU metric' }));
    expect(onChange).toHaveBeenCalledWith({ cpuUtilization: {} });

    rendered.rerender(<GceAutoscalingPolicyEditor policy={{ cpuUtilization: {} }} onChange={onChange} />);
    expect(screen.queryByTestId('cpu-target')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add CPU utilization metric' })).toBeInTheDocument();
  });

  it('switches scale-in units without losing a zero maximum', () => {
    const onChange = vi.fn();
    const policy = { scaleInControl: { maxScaledInReplicas: { percent: 0 }, timeWindowSec: 60 } };
    render(<GceAutoscalingPolicyEditor policy={policy} onChange={onChange} />);

    fireEvent.change(screen.getByTestId('scale-in-unit'), { target: { value: 'fixed' } });

    expect(onChange).toHaveBeenCalledWith({
      scaleInControl: { maxScaledInReplicas: { fixed: 0 }, timeWindowSec: 60 },
    });
  });

  it('edits scaling schedules including timezone and preserves disabled schedules', () => {
    const onChange = vi.fn();
    const policy = {
      scalingSchedules: [
        {
          scheduleName: 'overnight',
          enabled: false,
          minimumRequiredInstances: 0,
          timezone: 'Europe/London',
        },
      ],
    };
    render(<GceAutoscalingPolicyEditor policy={policy} onChange={onChange} />);

    expect(screen.getByTestId('schedule-enabled-0')).not.toBeChecked();
    expect(screen.getByTestId('schedule-minimum-0')).toHaveValue(0);
    fireEvent.change(screen.getByTestId('schedule-timezone-0'), { target: { value: 'Europe/Tallinn' } });

    expect(onChange).toHaveBeenCalledWith({
      scalingSchedules: [{ ...policy.scalingSchedules[0], timezone: 'Europe/Tallinn' }],
    });
  });
});
