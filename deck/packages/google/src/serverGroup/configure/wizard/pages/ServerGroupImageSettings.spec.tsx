import type { FormikProps } from 'formik';
import React from 'react';
import { shallow } from 'enzyme';

import { Markdown, StageArtifactSelectorDelegate } from '@spinnaker/core';

import { ServerGroupImageSettings } from './ServerGroupImageSettings';
import type { IGceServerGroupCommand } from '../GceServerGroupWizard.types';

describe('ServerGroupImageSettings', () => {
  it('renders accessible image and source controls while preserving unavailable persisted values', () => {
    const formik = testFormik();
    const wrapper = shallow(<ServerGroupImageSettings app={{ name: 'app' } as any} formik={formik} />);

    expect(selectOptions(wrapper, 'Image source')).toEqual([
      ['artifact', 'Artifact'],
      ['priorStage', 'Prior Stage'],
      ['persisted-source', 'persisted-source (unavailable)'],
    ]);
    expect(selectOptions(wrapper, 'Image')).toEqual([
      ['', 'Select...'],
      ['known-image', 'known-image'],
      ['persisted-image', 'persisted-image (unavailable)'],
    ]);
    expect(formik.setFieldValue).not.toHaveBeenCalled();
    expect(formik.setValues).not.toHaveBeenCalled();
  });

  it('updates image source and image selection as page-owned fields', () => {
    const formik = testFormik();
    const wrapper = shallow(<ServerGroupImageSettings app={{ name: 'app' } as any} formik={formik} />);

    wrapper.find('[aria-label="Image source"]').simulate('change', { target: { value: 'priorStage' } });
    wrapper.find('[aria-label="Image"]').simulate('change', { target: { value: 'known-image' } });

    expect(formik.setFieldValue.calls.allArgs()).toEqual([
      ['imageSource', 'priorStage'],
      ['image', 'known-image'],
    ]);
  });

  it('shows configured image source text instead of an editable source control', () => {
    const values = command({
      imageSource: 'artifact',
      viewState: { mode: 'editPipeline', showImageSourceSelector: true, imageSourceText: 'From **trigger**' },
    });
    const wrapper = shallow(<ServerGroupImageSettings app={{ name: 'app' } as any} formik={testFormik(values)} />);

    expect(wrapper.find('[aria-label="Image source"]').exists()).toBe(false);
    expect(wrapper.find(Markdown).prop('message')).toBe('From **trigger**');
    expect(wrapper.find(StageArtifactSelectorDelegate).exists()).toBe(true);
  });

  it('edits inline and expected image artifacts without retaining the other reference', () => {
    const values = command({
      imageSource: 'artifact',
      imageArtifactId: 'old-id',
      imageArtifact: { type: 'custom/object', reference: 'old' },
    });
    const formik = testFormik(values);
    const wrapper = shallow(<ServerGroupImageSettings app={{ name: 'app' } as any} formik={formik} />);
    const selector = wrapper.find(StageArtifactSelectorDelegate);
    const editedArtifact = { type: 'custom/object', reference: 'new' };

    selector.prop('onArtifactEdited')(editedArtifact as any);
    selector.prop('onExpectedArtifactSelected')({ id: 'expected-id' } as any);

    expect(formik.setFieldValue.calls.allArgs()).toEqual([
      ['imageArtifactId', null],
      ['imageArtifact', editedArtifact],
      ['imageArtifactId', 'expected-id'],
      ['imageArtifact', null],
    ]);
  });

  it('does not render or require image selection when it is disabled', () => {
    const values = command({ image: null, viewState: { mode: 'editPipeline', disableImageSelection: true } });
    const formik = testFormik(values);
    const page = new ServerGroupImageSettings({ app: { name: 'app' } as any, formik } as any);
    const wrapper = shallow(<ServerGroupImageSettings app={{ name: 'app' } as any} formik={formik} />);

    expect(wrapper.find('[aria-label="Image"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('Image selection is disabled for this command.');
    expect(page.validate(values)).toEqual({});
  });

  it('requires an image when selection is enabled', () => {
    const values = command({ image: '' });
    const formik = testFormik(values);
    const page = new ServerGroupImageSettings({ app: { name: 'app' } as any, formik } as any);

    expect(page.validate(values)).toEqual({ image: 'Image required.' });
  });

  it('renders image validation next to the required control with an accessible description', () => {
    const values = command({ image: '' });
    const wrapper = shallow(<ServerGroupImageSettings app={{ name: 'app' } as any} formik={testFormik(values)} />);
    const image = wrapper.find('select[aria-label="Image"]');

    expect(image.prop('required')).toBe(true);
    expect(image.prop('aria-invalid')).toBe(true);
    expect(image.prop('aria-describedby')).toBe('gce-server-group-image-error');
    expect(wrapper.find('#gce-server-group-image-error').prop('role')).toBe('alert');
    expect(wrapper.find('#gce-server-group-image-error').text()).toBe('Image required.');
  });
});

function selectOptions(wrapper: ReturnType<typeof shallow>, label: string): string[][] {
  return wrapper
    .find(`[aria-label="${label}"] option`)
    .map((option) => [option.prop('value') as string, option.text()]);
}

function testFormik(values = command()): FormikProps<IGceServerGroupCommand> {
  return ({
    values,
    setFieldValue: jasmine.createSpy('setFieldValue'),
    setValues: jasmine.createSpy('setValues'),
  } as unknown) as FormikProps<IGceServerGroupCommand>;
}

function command(overrides: Partial<IGceServerGroupCommand> = {}): IGceServerGroupCommand {
  return {
    application: 'app',
    credentials: 'account',
    regional: false,
    region: 'region',
    zone: 'zone',
    stack: 'main',
    freeFormDetails: 'detail',
    image: 'persisted-image',
    imageSource: 'persisted-source',
    capacity: { desired: 1 },
    distributionPolicy: { zones: [] },
    backingData: { allImages: [{ imageName: 'known-image' }] },
    viewState: {
      mode: 'editPipeline',
      showImageSourceSelector: true,
      pipeline: { stages: [] },
      stage: { type: 'deploy' },
    },
    ...overrides,
  };
}
