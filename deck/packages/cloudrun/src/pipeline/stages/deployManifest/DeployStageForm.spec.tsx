import { render, screen, waitFor } from '@testing-library/react';
import React from 'react';

import { yamlDocumentsToString } from '@spinnaker/core';
// eslint-disable-next-line @spinnaker/import-from-npm-not-relative
import { mockHttpClient } from '../../../../../core/src/api/mock/mockHttpSupport';

import { DeployStageForm } from './DeployStageForm';
import { ManifestSource } from '../../../manifest/ManifestSource';

// CodeMirror does not run under jsdom; a textarea exposes the value the real editor would show.
vi.mock('@spinnaker/core', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@spinnaker/core')>();
  return {
    ...actual,
    StageArtifactSelectorDelegate: () => null,
    YamlEditor: ({ value }: { value: string }) => <textarea data-testid="yaml-editor" readOnly value={value} />,
  };
});

// The form's other children fetch data or need a real Application; only the YAML editor matters here.
vi.mock('./ManifestBindArtifactsSelector', () => ({ ManifestBindArtifactsSelector: () => null }));
vi.mock('./serverGroupNamePreview', () => ({ ServerGroupNamePreview: () => null }));
vi.mock('../../../manifest/wizard/BasicSettings', () => ({ ManifestBasicSettings: () => null }));

const encode = (text: string) => btoa(unescape(encodeURIComponent(text)));
const flushPromises = () => new Promise((resolve) => setTimeout(resolve));

describe('<DeployStageForm /> stored manifest references', () => {
  const reference = { type: 'remote/map/base64', reference: 'ref://app/abc123', name: 'stored-entity' };
  const stored = { apiVersion: 'v1', kind: 'ConfigMap', metadata: { name: 'cm', namespace: 'default' } };
  const inline = { apiVersion: 'apps/v1', kind: 'Deployment', metadata: { name: 'web' } };

  let setFieldValue: ReturnType<typeof vi.fn>;

  const renderForm = (values: any) => {
    setFieldValue = vi.fn();
    const props: any = {
      accounts: [],
      application: { name: 'app' },
      formik: { values, setFieldValue },
      pipeline: {},
    };
    return render(<DeployStageForm {...props} />);
  };

  const editorValue = () => (screen.getByTestId('yaml-editor') as HTMLTextAreaElement).value;

  it('replaces a stored reference with the manifest YAML and updates the form', async () => {
    const http = mockHttpClient();
    http.expectGET('/artifacts/content-address/app/abc123').respond(200, { reference: encode(JSON.stringify(stored)) });

    renderForm({ source: ManifestSource.TEXT, manifests: [reference] });
    await http.flush();

    await waitFor(() => expect(setFieldValue).toHaveBeenCalledWith('manifests', [stored]));
    await waitFor(() => expect(editorValue()).toEqual(yamlDocumentsToString([stored])));
    expect(editorValue()).toContain('kind: ConfigMap');
    expect(editorValue()).not.toContain('ref://');
  });

  it('resolves only the references when stored and inline manifests are mixed', async () => {
    const http = mockHttpClient();
    http.expectGET('/artifacts/content-address/app/abc123').respond(200, { reference: encode(JSON.stringify(stored)) });

    renderForm({ source: ManifestSource.TEXT, manifests: [inline, reference] });
    await http.flush();

    await waitFor(() => expect(setFieldValue).toHaveBeenCalledWith('manifests', [inline, stored]));
  });

  it('leaves plain inline manifests untouched and makes no requests', async () => {
    // HTTP is fail-closed in specs, so any fetch here fails the test.
    renderForm({ source: ManifestSource.TEXT, manifests: [inline] });
    await flushPromises();

    expect(setFieldValue).not.toHaveBeenCalled();
    expect(editorValue()).toEqual(yamlDocumentsToString([inline]));
  });

  it('does not touch a manifest that merely looks like a stub but is not an entity-store reference', async () => {
    const lookalike = { type: 'embedded/base64', reference: 'ref://app/abc123', name: 'stored-entity' };
    renderForm({ source: ManifestSource.TEXT, manifests: [lookalike] });
    await flushPromises();

    expect(setFieldValue).not.toHaveBeenCalled();
    expect(editorValue()).toEqual(yamlDocumentsToString([lookalike]));
  });

  it('does nothing when there are no manifests', async () => {
    renderForm({ source: ManifestSource.TEXT });
    await flushPromises();

    expect(setFieldValue).not.toHaveBeenCalled();
    expect(editorValue()).toEqual('');
  });

  it('does not resolve references when the manifest source is an artifact', async () => {
    renderForm({ source: ManifestSource.ARTIFACT, manifests: [reference] });
    await flushPromises();

    expect(setFieldValue).not.toHaveBeenCalled();
  });

  it('keeps the reference in the editor when it cannot be fetched', async () => {
    const http = mockHttpClient();
    http.expectGET('/artifacts/content-address/app/abc123').respond(404, { message: 'not found' });

    renderForm({ source: ManifestSource.TEXT, manifests: [reference] });
    await http.flush();
    await flushPromises();

    expect(editorValue()).toEqual(yamlDocumentsToString([reference]));
    expect(setFieldValue).not.toHaveBeenCalledWith('manifests', [stored]);
  });

  it('ignores a late response once the form has unmounted', async () => {
    const http = mockHttpClient();
    http.expectGET('/artifacts/content-address/app/abc123').respond(200, { reference: encode(JSON.stringify(stored)) });

    const view = renderForm({ source: ManifestSource.TEXT, manifests: [reference] });
    view.unmount();
    await http.flush();
    await flushPromises();

    expect(setFieldValue).not.toHaveBeenCalled();
  });
});
