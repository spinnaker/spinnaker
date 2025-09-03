import { shallow } from 'enzyme';
import React from 'react';
import { Subject } from 'rxjs';

import { JobManifestPodLogs } from './JobManifestPodLogs';
import { JobStageExecutionLogs } from './JobStageExecutionLogs';
import { ManifestReader } from '../ManifestReader';
import type { IPodNameProvider } from '../PodNameProvider';
import type { Application } from '../../application';

describe('JobStageExecutionLogs', () => {
  const mockApplication = {} as Application;
  const mockManifest: {
    manifest: { metadata: { name: string; namespace: string }; spec: {}; status: {} };
    name: string;
    moniker: { app: string; cluster: string };
    account: string;
  } = {
    account: 'test-account',
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
  };
  const mockPodNamesProviders: IPodNameProvider[] = [
    {
      getPodName: () => 'test-pod',
    },
  ];

  let getManifestSpy: jasmine.Spy;
  const subject = new Subject();

  beforeEach(() => {
    getManifestSpy = spyOn(ManifestReader, 'getManifest').and.returnValue(subject);
  });

  afterEach(() => {
    getManifestSpy.calls.reset();
  });

  it('should fetch manifest on mount', () => {
    shallow(
      <JobStageExecutionLogs
        deployedName="test-job"
        account="test-account"
        application={mockApplication}
        externalLink=""
        podNamesProviders={mockPodNamesProviders}
        location="test-namespace"
      />,
    );

    expect(getManifestSpy).toHaveBeenCalledWith('test-account', 'test-namespace', 'test-job');
  });

  it('should render JobManifestPodLogs when location is provided and no externalLink', () => {
    const wrapper = shallow(
      <JobStageExecutionLogs
        deployedName="test-job"
        account="test-account"
        application={mockApplication}
        externalLink=""
        podNamesProviders={mockPodNamesProviders}
        location="test-namespace"
      />,
    );

    subject.next(mockManifest);
    wrapper.update();

    const podLogs = wrapper.find(JobManifestPodLogs);
    expect(podLogs.exists()).toBeTruthy();
    expect(podLogs.props()).toEqual({
      account: 'test-account',
      location: 'test-namespace',
      podNamesProviders: mockPodNamesProviders,
      linkName: 'Console Output',
    });
  });

  it('should not render JobManifestPodLogs when location is not provided', () => {
    const wrapper = shallow(
      <JobStageExecutionLogs
        deployedName="test-job"
        account="test-account"
        application={mockApplication}
        externalLink=""
        podNamesProviders={mockPodNamesProviders}
        location=""
      />,
    );

    subject.next(mockManifest);
    wrapper.update();

    expect(wrapper.find(JobManifestPodLogs).exists()).toBeFalsy();
  });

  it('should render external link when provided and manifest is not empty', () => {
    const wrapper = shallow(
      <JobStageExecutionLogs
        deployedName="test-job"
        account="test-account"
        application={mockApplication}
        externalLink="https://example.com/logs"
        podNamesProviders={mockPodNamesProviders}
        location="test-namespace"
      />,
    );

    subject.next(mockManifest);
    wrapper.update();

    const link = wrapper.find('a');
    expect(link.exists()).toBeTruthy();
    expect(link.prop('href')).toBe('https://example.com/logs');
    expect(link.text()).toBe('Console Output (External)');
  });

  it('should render external link with template variables', () => {
    const wrapper = shallow(
      <JobStageExecutionLogs
        deployedName="test-job"
        account="test-account"
        application={mockApplication}
        externalLink="https://example.com/logs/{{manifest.metadata.namespace}}/{{manifest.metadata.name}}"
        podNamesProviders={mockPodNamesProviders}
        location="test-namespace"
      />,
    );

    subject.next(mockManifest);
    wrapper.update();

    const link = wrapper.find('a');
    expect(link.exists()).toBeTruthy();
    expect(link.prop('href')).toBe('https://example.com/logs/test-namespace/test-job');
  });

  it('should not render external link with templates when manifest is empty', () => {
    const wrapper = shallow(
      <JobStageExecutionLogs
        deployedName="test-job"
        account="test-account"
        application={mockApplication}
        externalLink="https://example.com/logs/{{manifest.metadata.namespace}}/{{manifest.metadata.name}}"
        podNamesProviders={mockPodNamesProviders}
        location="test-namespace"
      />,
    );

    // Don't call subject.next() to simulate empty manifest state

    const podLogs = wrapper.find(JobManifestPodLogs);
    expect(podLogs.exists()).toBeTruthy();
    expect(wrapper.find('a').exists()).toBeFalsy();
  });

  it('should not template link if it does not include template syntax', () => {
    const externalLink = 'https://example.com/logs';
    const wrapper = shallow(
      <JobStageExecutionLogs
        deployedName="test-job"
        account="test-account"
        application={mockApplication}
        externalLink={externalLink}
        podNamesProviders={mockPodNamesProviders}
        location="test-namespace"
      />,
    );

    subject.next(mockManifest);
    wrapper.update();

    const link = wrapper.find('a');
    expect(link.prop('href')).toBe(externalLink);
  });

  it('should handle errors in manifest fetching gracefully', () => {
    const wrapper = shallow(
      <JobStageExecutionLogs
        deployedName="test-job"
        account="test-account"
        application={mockApplication}
        externalLink=""
        podNamesProviders={mockPodNamesProviders}
        location="test-namespace"
      />,
    );

    // Simulate an error
    subject.error(new Error('Failed to fetch manifest'));
    wrapper.update();

    // Component shouldn't crash and should render the JobManifestPodLogs as fallback
    expect(wrapper.find(JobManifestPodLogs).exists()).toBeTruthy();
  });
});
