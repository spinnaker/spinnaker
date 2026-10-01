import { render, screen } from '@testing-library/react';
import React from 'react';

import { SETTINGS } from '@spinnaker/core';

import type { IKubernetesLoadBalancerDetailsSectionProps } from './IKubernetesLoadBalancerDetailsSectionProps';
import { LoadBalancerStatusSection } from './LoadBalancerStatusSection';

describe('<LoadBalancerStatusSection/>', () => {
  let kubernetesSettings: any;

  beforeEach(() => {
    kubernetesSettings = SETTINGS.providers.kubernetes;
  });

  afterEach(() => {
    SETTINGS.providers.kubernetes = kubernetesSettings;
  });

  it('renders the default internal DNS name of the load balancer', () => {
    setTemplate(undefined);
    render(<LoadBalancerStatusSection loadBalancer={loadBalancer(manifestMetadata())} />);

    expect(fqdnLinks()).toHaveLength(1);
    expect(fqdnLinks()[0]).toHaveAttribute('href', 'http://backend.dev.svc.cluster.local');
    expect(screen.getAllByRole('textbox').map((node) => (node as HTMLTextAreaElement).value)).toContain(
      'backend.dev.svc.cluster.local',
    );
  });

  it('renders the internal DNS name from the configured template', () => {
    setTemplate('{{displayName}}-{{namespace}}.{{account | replace:"-cluster":""}}.example.com');
    render(<LoadBalancerStatusSection loadBalancer={loadBalancer(manifestMetadata())} />);

    expect(fqdnLinks()).toHaveLength(1);
    expect(fqdnLinks()[0]).toHaveAttribute('href', 'http://backend-dev.gke1.example.com');
  });

  it('omits the FQDN when the manifest metadata is missing a name or namespace', () => {
    const { container } = render(<LoadBalancerStatusSection loadBalancer={loadBalancer({})} />);

    expect(fqdnLinks()).toHaveLength(0);
    expect(container).not.toHaveTextContent('svc.cluster.local');
  });

  it('omits the FQDN when the manifest has no metadata at all', () => {
    render(<LoadBalancerStatusSection loadBalancer={loadBalancer(null)} />);

    expect(fqdnLinks()).toHaveLength(0);
  });

  function setTemplate(internalDNSNameTemplate?: string) {
    SETTINGS.providers.kubernetes = {
      ...kubernetesSettings,
      defaults: { ...kubernetesSettings?.defaults, internalDNSNameTemplate },
    };
  }
});

const fqdnLinks = () =>
  screen.queryAllByRole('link').filter((link) => String(link.getAttribute('href')).startsWith('http://'));

const manifestMetadata = () => ({ name: 'backend', namespace: 'dev' });

const loadBalancer = (metadata: any) =>
  (({
    account: 'gke1',
    displayName: 'service backend',
    kind: 'service',
    namespace: 'dev',
    serverGroups: [],
    instanceCounts: {},
    manifest: {
      account: 'gke1',
      manifest: {
        metadata,
        spec: {},
        status: { loadBalancer: {} },
      },
    },
  } as any) as IKubernetesLoadBalancerDetailsSectionProps['loadBalancer']);
