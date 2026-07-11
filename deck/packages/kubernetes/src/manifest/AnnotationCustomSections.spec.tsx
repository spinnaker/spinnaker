import { mount } from 'enzyme';
import React from 'react';

import { AnnotationCustomSections } from './AnnotationCustomSections';

describe('<AnnotationCustomSections />', () => {
  it('renders text annotations under section groups', () => {
    const wrapper = mount(
      <AnnotationCustomSections
        manifest={manifestWithAnnotations({
          'deployment-details.details.spinnaker.io/owner-name': 'Delivery Platform',
          'support.details.spinnaker.io/contact': 'On call',
        })}
        resource={resource()}
      />,
    );

    expect(wrapper.find('h4').at(0).text()).toContain('deployment details');
    expect(wrapper.find('h4').at(1).text()).toContain('support');
    expect(wrapper.text()).toContain('owner name');
    expect(wrapper.text()).toContain('Delivery Platform');
    expect(wrapper.text()).toContain('contact');
    expect(wrapper.text()).toContain('On call');
  });

  it('sanitizes HTML annotations while enforcing rel on targeted links', () => {
    const wrapper = mount(
      <AnnotationCustomSections
        manifest={manifestWithAnnotations({
          'links.details.html.spinnaker.io/runbook':
            '<a href="https://example.com/runbook" target="_blank" onclick="alert(1)">Runbook</a><a href="https://example.com/dashboard" target="dashboard">Dashboard</a><script>alert(2)</script>',
        })}
        resource={resource()}
      />,
    );

    const html = wrapper.html();
    expect(html).toContain('target="_blank"');
    expect(html).toContain('target="dashboard"');
    expect(html).toContain('rel="noopener noreferrer"');
    expect((html.match(/rel="noopener noreferrer"/g) || []).length).toBe(2);
    expect(html).toContain('Runbook');
    expect(html).toContain('Dashboard');
    expect(html).not.toContain('onclick');
    expect(html).not.toContain('<script>');
  });

  it('escapes interpolated resource values in HTML annotations', () => {
    const wrapper = mount(
      <AnnotationCustomSections
        manifest={manifestWithAnnotations({
          'summary.details.html.spinnaker.io/name': '<strong>Owner:</strong> {{ displayName }}',
        })}
        resource={resource({
          displayName: '<a href="https://evil.example" target="_blank">Evil</a>',
        })}
      />,
    );

    const html = wrapper.html();
    expect(html).toContain('<strong>Owner:</strong>');
    expect(html).toContain('&lt;a href="https://evil.example" target="_blank"&gt;Evil&lt;/a&gt;');
    expect(wrapper.find('a[href="https://evil.example"]').exists()).toBe(false);
  });

  it('interpolates path placeholders against resource and manifest values', () => {
    const wrapper = mount(
      <AnnotationCustomSections
        manifest={manifestWithAnnotations({
          'summary.details.spinnaker.io/name': '{{ displayName }} in {{ namespace }} from {{ manifest.metadata.name }}',
        })}
        resource={resource({ displayName: 'frontend', namespace: 'production' })}
      />,
    );

    expect(wrapper.text()).toContain('frontend in production from frontend-manifest');
  });

  it('leaves Angular expression placeholders unresolved instead of evaluating them as lodash paths', () => {
    const wrapper = mount(
      <AnnotationCustomSections
        manifest={manifestWithAnnotations({
          'summary.details.spinnaker.io/name': '{{ displayName | uppercase }} {{ getDisplayName() }}',
        })}
        resource={resource({
          displayName: 'frontend',
          getDisplayName: () => 'frontend',
        })}
      />,
    );

    expect(wrapper.text()).toContain('{{ displayName | uppercase }} {{ getDisplayName() }}');
  });

  it('leaves prototype-chain path placeholders unresolved', () => {
    const wrapper = mount(
      <AnnotationCustomSections
        manifest={manifestWithAnnotations({
          'summary.details.spinnaker.io/name':
            '{{ constructor }} {{ resource.constructor }} {{ resource["constructor"] }} {{ resource.__proto__ }} {{ resource.prototype }} {{ displayName }}',
        })}
        resource={resource({ displayName: 'frontend' })}
      />,
    );

    const text = wrapper.text();
    expect(text).toContain(
      '{{ constructor }} {{ resource.constructor }} {{ resource["constructor"] }} {{ resource.__proto__ }} {{ resource.prototype }} frontend',
    );
    expect(text).not.toContain('function Object');
    expect(text).not.toContain('[object Object]');
  });

  it('treats inherited function path values as missing', () => {
    const wrapper = mount(
      <AnnotationCustomSections
        manifest={manifestWithAnnotations({
          'summary.details.spinnaker.io/name': 'before {{ toString }} {{ resource.toString }} after {{ displayName }}',
        })}
        resource={resource({ displayName: 'frontend' })}
      />,
    );

    const text = wrapper.text();
    expect(text).toContain('before   after frontend');
    expect(text).not.toContain('function toString');
    expect(text).not.toContain('[native code]');
  });

  it('strips target attributes from sanitized non-anchor elements', () => {
    const wrapper = mount(
      <AnnotationCustomSections
        manifest={manifestWithAnnotations({
          'links.details.html.spinnaker.io/runbook':
            '<div target="_blank">Container</div><a href="https://example.com/runbook" target="_blank">Runbook</a>',
        })}
        resource={resource()}
      />,
    );

    const html = wrapper.html();
    expect(html).toContain('<div>Container</div>');
    expect(html).toContain('target="_blank"');
    expect(html).toContain('rel="noopener noreferrer"');
    expect(html).not.toContain('<div target="_blank">');
  });

  it('renders text entries before HTML entries within the same section', () => {
    const wrapper = mount(
      <AnnotationCustomSections
        manifest={manifestWithAnnotations({
          'runbook.details.html.spinnaker.io/link': '<a href="https://example.com" target="_blank">Runbook</a>',
          'runbook.details.spinnaker.io/summary': 'Read this first',
        })}
        resource={resource()}
      />,
    );

    const sectionText = wrapper.find('.content-body').at(0).text();
    expect(sectionText.indexOf('Read this first')).toBeLessThan(sectionText.indexOf('Runbook'));
  });
});

const manifestWithAnnotations = (annotations: { [key: string]: string }) => ({
  metadata: {
    name: 'frontend-manifest',
    annotations,
  },
});

const resource = (overrides: any = {}) =>
  ({
    apiVersion: 'apps/v1',
    displayName: 'frontend',
    kind: 'Deployment',
    namespace: 'default',
    ...overrides,
  } as any);
