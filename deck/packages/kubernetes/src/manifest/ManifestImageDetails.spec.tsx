import { mount } from 'enzyme';
import { load } from 'js-yaml';
import React from 'react';

import { CopyToClipboard } from '@spinnaker/core';

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
    const wrapper = component(manifest);
    const li = wrapper.find('li').at(0);
    expect(li.prop('title')).toEqual('nginx:1.9.9');
    expect(li.find(CopyToClipboard).prop('text')).toEqual('nginx:1.9.9');
    expect(li.text().trim()).toEqual('nginx:1.9.9 nginx:1.9.9');
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
    let wrapper = component(manifest);
    expect(wrapper.html()).not.toContain('Init Containers');
    expect(wrapper.html()).not.toContain('Containers');

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
    wrapper = component(manifest);
    expect(wrapper.html()).toContain('Init Containers');
    expect(wrapper.html()).toContain('Containers');
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
    const wrapper = component(manifest);
    const li = wrapper.find('li').at(0);
    expect(li.prop('title')).toEqual('busybox:latest');
    expect(li.find(CopyToClipboard).prop('text')).toEqual('busybox:latest');
    expect(li.text().trim()).toEqual('busybox:latest busybox:latest');
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
    const wrapper = component(manifest);
    const clipboards = wrapper.find(CopyToClipboard);
    expect(clipboards.length).toEqual(2);
    expect(clipboards.map((node) => node.prop('text'))).toEqual(['busybox:latest', 'nginx:1.9.9']);
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
    const wrapper = component(manifest);
    const texts = wrapper.find(CopyToClipboard).map((node) => node.prop('text'));
    expect(texts).toEqual(['nginx:1.9.9', 'busybox:latest']);
  });
});
const component = (manifest: string) => mount((<ManifestImageDetails manifest={load(manifest)} />) as any);
