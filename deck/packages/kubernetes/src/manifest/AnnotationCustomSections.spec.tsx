import { render, screen } from '@testing-library/react';
import React from 'react';

import { AnnotationCustomSections } from './AnnotationCustomSections';

describe('<AnnotationCustomSections />', () => {
  it('renders text annotations under section groups', () => {
    render(
      <AnnotationCustomSections
        manifest={manifestWithAnnotations({
          'deployment-details.details.spinnaker.io/owner-name': 'Delivery Platform',
          'support.details.spinnaker.io/contact': 'On call',
        })}
        resource={resource()}
      />,
    );

    expect(screen.getByRole('heading', { name: 'deployment details' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'support' })).toBeInTheDocument();
    expect(screen.getByText('owner name')).toBeInTheDocument();
    expect(screen.getByText('Delivery Platform')).toBeInTheDocument();
    expect(screen.getByText('contact')).toBeInTheDocument();
    expect(screen.getByText('On call')).toBeInTheDocument();
  });

  it('sanitizes HTML annotations while enforcing rel on targeted links', () => {
    const { container } = render(
      <AnnotationCustomSections
        manifest={manifestWithAnnotations({
          'links.details.html.spinnaker.io/runbook':
            '<a href="https://example.com/runbook" target="_blank" onclick="alert(1)">Runbook</a><a href="https://example.com/dashboard" target="dashboard">Dashboard</a><script>alert(2)</script>',
        })}
        resource={resource()}
      />,
    );

    const links = screen.getAllByRole('link');
    expect(links).toHaveLength(2);
    links.forEach((link) => {
      expect(link).toHaveAttribute('target', '_blank');
      expect(link).toHaveAttribute('rel', 'noopener noreferrer');
      expect(link).not.toHaveAttribute('onclick');
    });
    expect(screen.getByRole('link', { name: 'Runbook' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Dashboard' })).toBeInTheDocument();
    expect(container.querySelector('[target="dashboard"]')).not.toBeInTheDocument();
    expect(container.querySelector('script')).not.toBeInTheDocument();
  });

  it('escapes interpolated resource values in HTML annotations', () => {
    const { container } = render(
      <AnnotationCustomSections
        manifest={manifestWithAnnotations({
          'summary.details.html.spinnaker.io/name': '<strong>Owner:</strong> {{ displayName }}',
        })}
        resource={resource({
          displayName: '<a href="https://evil.example" target="_blank">Evil</a>',
        })}
      />,
    );

    expect(screen.getByText('Owner:')).toBeInTheDocument();
    expect(container).toHaveTextContent('<a href="https://evil.example" target="_blank">Evil</a>');
    expect(container.querySelector('a[href="https://evil.example"]')).not.toBeInTheDocument();
  });

  it('interpolates path placeholders against resource and manifest values', () => {
    render(
      <AnnotationCustomSections
        manifest={manifestWithAnnotations({
          'summary.details.spinnaker.io/name': '{{ displayName }} in {{ namespace }} from {{ manifest.metadata.name }}',
        })}
        resource={resource({ displayName: 'frontend', namespace: 'production' })}
      />,
    );

    expect(screen.getByText('frontend in production from frontend-manifest')).toBeInTheDocument();
  });

  it('leaves non-path expression placeholders unresolved', () => {
    render(
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

    expect(screen.getByText('{{ displayName | uppercase }} {{ getDisplayName() }}')).toBeInTheDocument();
  });

  it('leaves prototype-chain path placeholders unresolved', () => {
    const { container } = render(
      <AnnotationCustomSections
        manifest={manifestWithAnnotations({
          'summary.details.spinnaker.io/name':
            '{{ constructor }} {{ resource.constructor }} {{ resource["constructor"] }} {{ resource.__proto__ }} {{ resource.prototype }} {{ displayName }}',
        })}
        resource={resource({ displayName: 'frontend' })}
      />,
    );

    const text = container.textContent || '';
    expect(text).toContain(
      '{{ constructor }} {{ resource.constructor }} {{ resource["constructor"] }} {{ resource.__proto__ }} {{ resource.prototype }} frontend',
    );
    expect(text).not.toContain('function Object');
    expect(text).not.toContain('[object Object]');
  });

  it('treats inherited function path values as missing', () => {
    const { container } = render(
      <AnnotationCustomSections
        manifest={manifestWithAnnotations({
          'summary.details.spinnaker.io/name': 'before {{ toString }} {{ resource.toString }} after {{ displayName }}',
        })}
        resource={resource({ displayName: 'frontend' })}
      />,
    );

    const text = container.textContent || '';
    expect(text).toContain('before   after frontend');
    expect(text).not.toContain('function toString');
    expect(text).not.toContain('[native code]');
  });

  it('strips target attributes from sanitized non-anchor elements', () => {
    const { container } = render(
      <AnnotationCustomSections
        manifest={manifestWithAnnotations({
          'links.details.html.spinnaker.io/runbook':
            '<div target="_blank">Container</div><a href="https://example.com/runbook" target="_blank">Runbook</a>',
        })}
        resource={resource()}
      />,
    );

    const content = screen.getByText('Container');
    const link = screen.getByRole('link', { name: 'Runbook' });
    expect(content).not.toHaveAttribute('target');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
    expect(container.querySelector('div[target]')).not.toBeInTheDocument();
  });

  it('renders text entries before HTML entries within the same section', () => {
    const { container } = render(
      <AnnotationCustomSections
        manifest={manifestWithAnnotations({
          'runbook.details.html.spinnaker.io/link': '<a href="https://example.com" target="_blank">Runbook</a>',
          'runbook.details.spinnaker.io/summary': 'Read this first',
        })}
        resource={resource()}
      />,
    );

    const sectionText = container.querySelector('.content-body')?.textContent || '';
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
