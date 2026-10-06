import { act, render, screen } from '@testing-library/react';
import { setupUser } from '../../utils/testUtils/userEvent';
import React from 'react';
import { Subject } from 'rxjs';
import type { Mock } from 'vitest';

import { JobStageExecutionLogs } from './JobStageExecutionLogs';
import { ManifestReader } from '../ManifestReader';
import type { IPodNameProvider } from '../PodNameProvider';
import type { Application } from '../../application';
import { ApplicationModelBuilder } from '../../application/applicationModel.builder';
import type { IManifest } from '../../domain/IManifest';
import { InstanceReader } from '../../instance/InstanceReader';

describe('JobStageExecutionLogs', () => {
  const mockManifest: IManifest = {
    account: 'test-account',
    artifacts: [],
    cloudProvider: 'kubernetes',
    events: [],
    location: 'test-namespace',
    name: 'test-manifest',
    moniker: {
      app: 'testapp',
      cluster: 'testcluster',
    },
    manifest: {
      metadata: {
        name: 'test-job',
        namespace: 'test-namespace',
      },
      spec: {},
      status: {},
    },
    status: {},
  };
  const mockPodNamesProviders: IPodNameProvider[] = [
    {
      getPodName: () => 'test-pod',
    },
  ];

  let getManifestSpy: Mock;
  let mockApplication: Application;
  let subject: Subject<IManifest>;

  const renderLogs = (externalLink = '', location = 'test-namespace') =>
    render(
      <JobStageExecutionLogs
        deployedName="test-job"
        account="test-account"
        application={mockApplication}
        externalLink={externalLink}
        podNamesProviders={mockPodNamesProviders}
        location={location}
      />,
    );

  beforeEach(() => {
    mockApplication = ApplicationModelBuilder.createApplicationForTests('test-app');
    subject = new Subject<IManifest>();
    getManifestSpy = vi.spyOn(ManifestReader, 'getManifest').mockReturnValue(subject);
  });

  afterEach(() => subject.complete());

  it('should fetch manifest on mount and release the reader on unmount', () => {
    const { unmount } = renderLogs();

    expect(getManifestSpy).toHaveBeenCalledWith('test-account', 'test-namespace', 'test-job');
    expect(subject.observers).toHaveLength(1);

    unmount();
    expect(subject.observers).toHaveLength(0);

    act(() => subject.next(mockManifest));
    expect(subject.observers).toHaveLength(0);
  });

  it('should render JobManifestPodLogs when location is provided and no externalLink', async () => {
    const user = setupUser();
    vi.spyOn(InstanceReader, 'getConsoleOutput').mockResolvedValue({ output: [] });
    renderLogs();

    act(() => subject.next(mockManifest));
    await user.click(screen.getByText('Console Output'));

    expect(InstanceReader.getConsoleOutput).toHaveBeenCalledWith(
      'test-account',
      'test-namespace',
      'pod test-pod',
      'kubernetes',
    );
    expect(await screen.findByText('Console Output', { selector: '.modal-title' })).toBeInTheDocument();
  });

  it('should not render JobManifestPodLogs when location is not provided', () => {
    const { container } = renderLogs('', '');

    act(() => subject.next(mockManifest));

    expect(container).toHaveTextContent('');
    expect(container.querySelector('*')).not.toBeInTheDocument();
  });

  it('should render external link when provided and manifest is not empty', () => {
    renderLogs('https://example.com/logs');

    act(() => subject.next(mockManifest));

    expect(screen.getByRole('link', { name: 'Console Output (External)' })).toHaveAttribute(
      'href',
      'https://example.com/logs',
    );
  });

  it('should render external link with template variables', () => {
    renderLogs('https://example.com/logs/{{manifest.metadata.namespace}}/{{manifest.metadata.name}}');

    act(() => subject.next(mockManifest));

    expect(screen.getByRole('link', { name: 'Console Output (External)' })).toHaveAttribute(
      'href',
      'https://example.com/logs/test-namespace/test-job',
    );
  });

  it('should not render external link with templates when manifest is empty', () => {
    renderLogs('https://example.com/logs/{{manifest.metadata.namespace}}/{{manifest.metadata.name}}');

    expect(screen.getByText('Console Output')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Console Output (External)' })).not.toBeInTheDocument();
  });

  it('should not template link if it does not include template syntax', () => {
    renderLogs('https://example.com/logs');

    act(() => subject.next(mockManifest));

    expect(screen.getByRole('link', { name: 'Console Output (External)' })).toHaveAttribute(
      'href',
      'https://example.com/logs',
    );
  });

  it('should handle errors in manifest fetching gracefully', () => {
    renderLogs();

    act(() => subject.error(new Error('Failed to fetch manifest')));

    expect(screen.getByText('Console Output')).toBeInTheDocument();
  });
});
