import { shallow } from 'enzyme';
import React from 'react';

import { yamlDocumentsToString, YamlEditor } from '@spinnaker/core';
import { mockHttpClient } from 'core/api/mock/jasmine';

import { DeployStageForm } from './DeployStageForm';
import { ManifestSource } from '../../../manifest/ManifestSource';

const encode = (text: string) => btoa(unescape(encodeURIComponent(text)));
const flushPromises = () => new Promise((resolve) => setTimeout(resolve));

describe('<DeployStageForm /> stored manifest references', () => {
  const reference = { type: 'remote/map/base64', reference: 'ref://app/abc123', name: 'stored-entity' };
  const stored = { apiVersion: 'v1', kind: 'ConfigMap', metadata: { name: 'cm', namespace: 'default' } };
  const inline = { apiVersion: 'apps/v1', kind: 'Deployment', metadata: { name: 'web' } };

  let setFieldValue: jasmine.Spy;

  const render = (values: any) => {
    setFieldValue = jasmine.createSpy('setFieldValue');
    const props: any = {
      accounts: [],
      application: { name: 'app' },
      formik: { values, setFieldValue },
      pipeline: {},
    };
    return shallow(<DeployStageForm {...props} />);
  };

  const editorValue = (wrapper: any) => wrapper.update().find(YamlEditor).prop('value');

  it('replaces a stored reference with the manifest YAML and updates the form', async () => {
    const http = mockHttpClient();
    http.expectGET('/artifacts/content-address/app/abc123').respond(200, { reference: encode(JSON.stringify(stored)) });

    const wrapper = render({ source: ManifestSource.TEXT, manifests: [reference] });
    await http.flush();
    await flushPromises();

    expect(setFieldValue).toHaveBeenCalledWith('manifests', [stored]);
    expect(editorValue(wrapper)).toEqual(yamlDocumentsToString([stored]));
    expect(editorValue(wrapper)).toContain('kind: ConfigMap');
    expect(editorValue(wrapper)).not.toContain('ref://');
  });

  it('resolves only the references when stored and inline manifests are mixed', async () => {
    const http = mockHttpClient();
    http.expectGET('/artifacts/content-address/app/abc123').respond(200, { reference: encode(JSON.stringify(stored)) });

    render({ source: ManifestSource.TEXT, manifests: [inline, reference] });
    await http.flush();
    await flushPromises();

    expect(setFieldValue).toHaveBeenCalledWith('manifests', [inline, stored]);
  });

  it('leaves plain inline manifests untouched and makes no requests', async () => {
    // HTTP is fail-closed in specs, so any fetch here fails the test.
    const wrapper = render({ source: ManifestSource.TEXT, manifests: [inline] });
    await flushPromises();

    expect(setFieldValue).not.toHaveBeenCalled();
    expect(editorValue(wrapper)).toEqual(yamlDocumentsToString([inline]));
  });

  it('does not touch a manifest that merely looks like a stub but is not an entity-store reference', async () => {
    const lookalike = { type: 'embedded/base64', reference: 'ref://app/abc123', name: 'stored-entity' };
    const wrapper = render({ source: ManifestSource.TEXT, manifests: [lookalike] });
    await flushPromises();

    expect(setFieldValue).not.toHaveBeenCalled();
    expect(editorValue(wrapper)).toEqual(yamlDocumentsToString([lookalike]));
  });

  it('does nothing when there are no manifests', async () => {
    const wrapper = render({ source: ManifestSource.TEXT });
    await flushPromises();

    expect(setFieldValue).not.toHaveBeenCalled();
    expect(editorValue(wrapper)).toEqual('');
  });

  it('does not resolve references when the manifest source is an artifact', async () => {
    render({ source: ManifestSource.ARTIFACT, manifests: [reference] });
    await flushPromises();

    expect(setFieldValue).not.toHaveBeenCalled();
  });

  it('keeps the reference in the editor when it cannot be fetched', async () => {
    const http = mockHttpClient();
    http.expectGET('/artifacts/content-address/app/abc123').respond(404, { message: 'not found' });

    const wrapper = render({ source: ManifestSource.TEXT, manifests: [reference] });
    await http.flush();
    await flushPromises();

    expect(editorValue(wrapper)).toEqual(yamlDocumentsToString([reference]));
    expect(setFieldValue).not.toHaveBeenCalledWith('manifests', [stored]);
  });

  it('ignores a late response once the form has unmounted', async () => {
    const http = mockHttpClient();
    http.expectGET('/artifacts/content-address/app/abc123').respond(200, { reference: encode(JSON.stringify(stored)) });

    const wrapper = render({ source: ManifestSource.TEXT, manifests: [reference] });
    wrapper.unmount();
    await http.flush();
    await flushPromises();

    expect(setFieldValue).not.toHaveBeenCalled();
  });
});
