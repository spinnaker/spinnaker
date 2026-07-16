import React from 'react';
import { shallow } from 'enzyme';

import { GcePredictiveMethod } from './IGceAutoscalingPolicy';
import { GceAutoscalingPolicyEditor } from './GceAutoscalingPolicyEditor';

describe('GceAutoscalingPolicyEditor', () => {
  it('renders zero values without replacing them with blanks', () => {
    const wrapper = shallow(
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

    expect(wrapper.find('[data-testid="minimum-replicas"]').prop('value')).toBe(0);
    expect(wrapper.find('[data-testid="cpu-target"]').prop('value')).toBe(0);
    expect(wrapper.find('[data-testid="scale-in-maximum"]').prop('value')).toBe(0);
    expect(wrapper.find('[data-testid="predictive-autoscaling"]').prop('checked')).toBe(false);
  });

  it('keeps the predictive setting behind its feature gate and writes NONE when disabled', () => {
    const onChange = jasmine.createSpy('onChange');
    const policy = {
      cpuUtilization: { utilizationTarget: 0.5, predictiveMethod: GcePredictiveMethod.STANDARD },
    };
    const hidden = shallow(
      <GceAutoscalingPolicyEditor policy={policy} onChange={onChange} predictiveAutoscalingEnabled={false} />,
    );
    expect(hidden.find('[data-testid="predictive-autoscaling"]').exists()).toBe(false);

    const visible = shallow(
      <GceAutoscalingPolicyEditor policy={policy} onChange={onChange} predictiveAutoscalingEnabled={true} />,
    );
    visible.find('[data-testid="predictive-autoscaling"]').simulate('change', { target: { checked: false } });

    expect(onChange).toHaveBeenCalledWith({
      cpuUtilization: { utilizationTarget: 0.5, predictiveMethod: GcePredictiveMethod.NONE },
    });
  });

  it('edits CPU, HTTP load-balancing, and custom metrics as controlled values', () => {
    const onChange = jasmine.createSpy('onChange');
    const policy = {
      cpuUtilization: { utilizationTarget: 0.5 },
      loadBalancingUtilization: { utilizationTarget: 0.6 },
      customMetricUtilizations: [{ metric: 'custom.googleapis.com/queue', utilizationTarget: 3 }],
    };
    const wrapper = shallow(<GceAutoscalingPolicyEditor policy={policy} onChange={onChange} />);

    wrapper.find('[data-testid="cpu-target"]').simulate('change', { target: { value: '0' } });
    expect(onChange).toHaveBeenCalledWith({ ...policy, cpuUtilization: { utilizationTarget: 0 } });

    wrapper.find('[data-testid="http-lb-target"]').simulate('change', { target: { value: '75' } });
    expect(onChange).toHaveBeenCalledWith({ ...policy, loadBalancingUtilization: { utilizationTarget: 0.75 } });

    wrapper.find('[data-testid="custom-metric-target-0"]').simulate('change', { target: { value: '0' } });
    expect(onChange).toHaveBeenCalledWith({
      ...policy,
      customMetricUtilizations: [{ metric: 'custom.googleapis.com/queue', utilizationTarget: 0 }],
    });
  });

  it('clears group-only scaling fields when a custom metric changes to per-instance scope', () => {
    const onChange = jasmine.createSpy('onChange');
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
    const wrapper = shallow(<GceAutoscalingPolicyEditor policy={policy} onChange={onChange} />);

    wrapper.find('[aria-label="Metric export scope"]').simulate('change', {
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
    const onChange = jasmine.createSpy('onChange');
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
    const wrapper = shallow(<GceAutoscalingPolicyEditor policy={policy} onChange={onChange} />);

    wrapper.find('[aria-label="Metric export scope"]').simulate('change', {
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
    const onChange = jasmine.createSpy('onChange');
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
    const wrapper = shallow(<GceAutoscalingPolicyEditor policy={policy} onChange={onChange} />);

    wrapper.find('[aria-label="Scaling policy"]').simulate('change', { target: { value: 'UTILIZATION_TARGET' } });

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
    const onChange = jasmine.createSpy('onChange');
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
    const wrapper = shallow(<GceAutoscalingPolicyEditor policy={policy} onChange={onChange} />);

    wrapper.find('[aria-label="Scaling policy"]').simulate('change', {
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
    const onChange = jasmine.createSpy('onChange');
    const wrapper = shallow(
      <GceAutoscalingPolicyEditor policy={{ cpuUtilization: { utilizationTarget: 0.5 } }} onChange={onChange} />,
    );

    wrapper
      .find('button')
      .filterWhere((button) => button.text() === 'Delete CPU metric')
      .simulate('click');
    expect(onChange).toHaveBeenCalledWith({ cpuUtilization: {} });

    wrapper.setProps({ policy: { cpuUtilization: {} } });
    expect(wrapper.find('[data-testid="cpu-target"]').exists()).toBe(false);
    expect(
      wrapper
        .find('button')
        .filterWhere((button) => button.text() === 'Add CPU utilization metric')
        .exists(),
    ).toBe(true);
  });

  it('switches scale-in units without losing a zero maximum', () => {
    const onChange = jasmine.createSpy('onChange');
    const policy = { scaleInControl: { maxScaledInReplicas: { percent: 0 }, timeWindowSec: 60 } };
    const wrapper = shallow(<GceAutoscalingPolicyEditor policy={policy} onChange={onChange} />);

    wrapper.find('[data-testid="scale-in-unit"]').simulate('change', { target: { value: 'fixed' } });

    expect(onChange).toHaveBeenCalledWith({
      scaleInControl: { maxScaledInReplicas: { fixed: 0 }, timeWindowSec: 60 },
    });
  });

  it('edits scaling schedules including timezone and preserves disabled schedules', () => {
    const onChange = jasmine.createSpy('onChange');
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
    const wrapper = shallow(<GceAutoscalingPolicyEditor policy={policy} onChange={onChange} />);

    expect(wrapper.find('[data-testid="schedule-enabled-0"]').prop('checked')).toBe(false);
    expect(wrapper.find('[data-testid="schedule-minimum-0"]').prop('value')).toBe(0);
    wrapper.find('[data-testid="schedule-timezone-0"]').simulate('change', { target: { value: 'Europe/Tallinn' } });

    expect(onChange).toHaveBeenCalledWith({
      scalingSchedules: [{ ...policy.scalingSchedules[0], timezone: 'Europe/Tallinn' }],
    });
  });
});
