import { render } from '@testing-library/react';
import * as React from 'react';

import type { IStage } from '@spinnaker/core';

import { ReportLink } from './canaryRunSummaries';

describe('<ReportLink />', () => {
  const href = vi.fn(() => '#/canary/report/cfg/exec');

  beforeEach(() => href.mockClear());

  const renderLink = (status: string, context: any) =>
    render(<ReportLink canaryRun={({ status, context } as any) as IStage} stateService={{ href }} />);

  it('renders a link to the report for a finished run', () => {
    const { container } = renderLink('SUCCEEDED', { canaryConfigId: 'cfg', canaryPipelineExecutionId: 'exec' });
    const links = container.querySelectorAll('a');
    expect(links.length).toBe(1);
    expect(links[0].getAttribute('href')).toBe('#/canary/report/cfg/exec');
    expect(links[0].getAttribute('aria-label')).toBe('View canary report');
    expect(href).toHaveBeenCalledWith('home.applications.application.canary.report.reportDetail', {
      configId: 'cfg',
      runId: 'exec',
    });
  });

  it('renders nothing while the run is RUNNING', () => {
    const { container } = renderLink('RUNNING', { canaryConfigId: 'cfg', canaryPipelineExecutionId: 'exec' });
    expect(container.querySelectorAll('a').length).toBe(0);
    expect(href).not.toHaveBeenCalled();
  });

  it('renders nothing when the config id is missing', () => {
    const { container } = renderLink('SUCCEEDED', { canaryPipelineExecutionId: 'exec' });
    expect(container.querySelectorAll('a').length).toBe(0);
  });

  it('renders nothing when the execution id is missing', () => {
    const { container } = renderLink('SUCCEEDED', { canaryConfigId: 'cfg' });
    expect(container.querySelectorAll('a').length).toBe(0);
  });
});
