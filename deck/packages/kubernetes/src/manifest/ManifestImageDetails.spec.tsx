import { render, screen, within } from '@testing-library/react';
import { load } from 'js-yaml';
import React from 'react';

import { ManifestImageDetails } from '../manifest/ManifestImageDetails';

describe('<ManifestImageDetails />', () => {
  it('renders image names', () => {
    const manifest = `
      apiVersion: extensions/v1beta1
      kind: Deployment
      spec:
        template:
          spec:
            containers:
              - image: 'nginx:1.9.9'
                imagePullPolicy: IfNotPresent
                name: nginx`;
    component(manifest);

    const li = screen.getAllByRole('listitem')[0];
    expect(li).toHaveAttribute('title', 'nginx:1.9.9');
    expect(clipboardTexts(li)).toEqual(['nginx:1.9.9']);
    expect(li.textContent.trim()).toEqual('nginx:1.9.9 nginx:1.9.9');
  });

  it('separates `containers` and `initContainers` if both are present', () => {
    let manifest = `
      apiVersion: extensions/v1beta1
      kind: Deployment
      spec:
        template:
          spec:
            containers:
              - image: 'nginx:1.9.9'
                imagePullPolicy: IfNotPresent
                name: nginx`;
    const { rerender } = component(manifest);
    expect(screen.queryByText('Init Containers')).not.toBeInTheDocument();
    expect(screen.queryByText('Containers')).not.toBeInTheDocument();

    manifest = `
      apiVersion: extensions/v1beta1
      kind: Deployment
      spec:
        template:
          spec:
            containers:
              - image: 'nginx:1.9.9'
                imagePullPolicy: IfNotPresent
                name: nginx
            initContainers:
              - command:
                  - echo
                  - helloworld
                image: busybox
                name: init-deployment`;
    rerender(<ManifestImageDetails manifest={load(manifest) as any} />);
    expect(screen.getByText('Init Containers')).toBeInTheDocument();
    expect(screen.getByText('Containers')).toBeInTheDocument();
  });

  it('appends `:latest` to an image without a tag or digest', () => {
    const manifest = `
      apiVersion: extensions/v1beta1
      kind: Deployment
      spec:
        template:
          spec:
            containers:
              - image: busybox
                imagePullPolicy: IfNotPresent
                name: busybox`;
    component(manifest);

    const li = screen.getAllByRole('listitem')[0];
    expect(li).toHaveAttribute('title', 'busybox:latest');
    expect(clipboardTexts(li)).toEqual(['busybox:latest']);
    expect(li.textContent.trim()).toEqual('busybox:latest busybox:latest');
  });

  it('adds a copy-to-clipboard button with the normalized image for each container', () => {
    const manifest = `
      apiVersion: extensions/v1beta1
      kind: Deployment
      spec:
        template:
          spec:
            containers:
              - image: 'nginx:1.9.9'
                imagePullPolicy: IfNotPresent
                name: nginx
              - image: busybox
                imagePullPolicy: IfNotPresent
                name: busybox`;
    component(manifest);

    expect(screen.getAllByRole('button', { name: 'Copy to clipboard' })).toHaveLength(2);
    expect(clipboardTexts(document.body)).toEqual(['busybox:latest', 'nginx:1.9.9']);
  });

  it('adds a copy-to-clipboard button for init container images too', () => {
    const manifest = `
      apiVersion: extensions/v1beta1
      kind: Deployment
      spec:
        template:
          spec:
            containers:
              - image: 'nginx:1.9.9'
                imagePullPolicy: IfNotPresent
                name: nginx
            initContainers:
              - command:
                  - echo
                  - helloworld
                image: busybox
                name: init-deployment`;
    component(manifest);

    expect(screen.getAllByRole('button', { name: 'Copy to clipboard' })).toHaveLength(2);
    expect(clipboardTexts(document.body)).toEqual(['nginx:1.9.9', 'busybox:latest']);
  });
});
const component = (manifest: string) => render(<ManifestImageDetails manifest={load(manifest) as any} />);
const clipboardTexts = (root: HTMLElement) =>
  within(root)
    .getAllByRole('textbox')
    .map((node) => (node as HTMLTextAreaElement).value);
