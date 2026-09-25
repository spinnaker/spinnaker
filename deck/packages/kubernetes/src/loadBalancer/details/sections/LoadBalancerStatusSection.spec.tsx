import { shallow } from 'enzyme';
import React from 'react';

import { CopyToClipboard, SETTINGS } from '@spinnaker/core';

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
    const component = shallow(<LoadBalancerStatusSection loadBalancer={loadBalancer(manifestMetadata())} />);

    expect(fqdnLink(component).prop('href')).toEqual('http://backend.dev.svc.cluster.local');
    expect(component.find(CopyToClipboard).map((node) => node.prop('text'))).toContain('backend.dev.svc.cluster.local');
  });

  it('renders the internal DNS name from the configured template', () => {
    setTemplate('{{displayName}}-{{namespace}}.{{account | replace:"-cluster":""}}.example.com');
    const component = shallow(<LoadBalancerStatusSection loadBalancer={loadBalancer(manifestMetadata())} />);

    expect(fqdnLink(component).prop('href')).toEqual('http://backend-dev.gke1.example.com');
  });

  it('omits the FQDN when the manifest metadata is missing a name or namespace', () => {
    const component = shallow(<LoadBalancerStatusSection loadBalancer={loadBalancer({})} />);

    expect(fqdnLink(component).length).toEqual(0);
    expect(component.text()).not.toContain('svc.cluster.local');
  });

  it('omits the FQDN when the manifest has no metadata at all', () => {
    const component = shallow(<LoadBalancerStatusSection loadBalancer={loadBalancer(null)} />);

    expect(fqdnLink(component).length).toEqual(0);
  });

  function setTemplate(internalDNSNameTemplate?: string) {
    SETTINGS.providers.kubernetes = {
      ...kubernetesSettings,
      defaults: { ...kubernetesSettings?.defaults, internalDNSNameTemplate },
    };
  }
});

const fqdnLink = (component: any) =>
  component.find('a').filterWhere((link: any) => String(link.prop('href')).startsWith('http://'));

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
