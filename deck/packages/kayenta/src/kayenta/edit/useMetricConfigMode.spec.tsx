import { render, screen } from '@testing-library/react';
import * as React from 'react';

import { CanarySettings } from '../canary.settings';
import { MetricConfigMode } from '../domain/IMetricConfigMode';
import { deriveMetricConfigMode, useMetricConfigMode } from './useMetricConfigMode';

describe('deriveMetricConfigMode', () => {
  it('prefers Template when template data is present', () => {
    expect(deriveMetricConfigMode(true, true)).toBe(MetricConfigMode.TEMPLATE);
    expect(deriveMetricConfigMode(true, false)).toBe(MetricConfigMode.TEMPLATE);
  });

  it('uses Guided for existing guided-only metrics', () => {
    expect(deriveMetricConfigMode(false, true)).toBe(MetricConfigMode.GUIDED);
  });

  it('defaults brand-new empty metrics to Template', () => {
    expect(deriveMetricConfigMode(false, false)).toBe(MetricConfigMode.TEMPLATE);
  });
});

const modeFor = (hasTemplateData: boolean, hasGuidedData: boolean): string => {
  const Probe = () => <span data-testid="mode">{useMetricConfigMode('m1', hasTemplateData, hasGuidedData)[0]}</span>;
  const { unmount } = render(<Probe />);
  const mode = screen.getByTestId('mode').textContent;
  unmount();
  return mode;
};

describe('useMetricConfigMode', () => {
  const original = CanarySettings.templatesEnabled;

  afterEach(() => {
    CanarySettings.templatesEnabled = original;
  });

  it('is pinned to Guided when templatesEnabled is false', () => {
    CanarySettings.templatesEnabled = false;
    expect(modeFor(true, false)).toBe(MetricConfigMode.GUIDED);
  });

  it('defaults to Template for new metrics when templatesEnabled is not set', () => {
    CanarySettings.templatesEnabled = undefined;
    expect(modeFor(false, false)).toBe(MetricConfigMode.TEMPLATE);
    // existing guided metrics keep opening in Guided
    expect(modeFor(false, true)).toBe(MetricConfigMode.GUIDED);
  });

  it('derives the mode from the metric data when templatesEnabled is true', () => {
    CanarySettings.templatesEnabled = true;
    expect(modeFor(true, false)).toBe(MetricConfigMode.TEMPLATE);
    expect(modeFor(false, true)).toBe(MetricConfigMode.GUIDED);
  });
});
