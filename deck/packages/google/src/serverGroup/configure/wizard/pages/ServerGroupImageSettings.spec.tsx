import { fireEvent, render, screen, within } from '@testing-library/react';
import React from 'react';

import type { IGceServerGroupCommand } from '../GceServerGroupWizard.types';
import { ServerGroupImageSettings, validateGceServerGroupImageSettings } from './ServerGroupImageSettings';

vi.mock('@spinnaker/core', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@spinnaker/core')>();
  return {
    ...actual,
    StageArtifactSelectorDelegate: ({ onArtifactEdited, onExpectedArtifactSelected }: any) => (
      <div data-testid="artifact-selector">
        <button onClick={() => onArtifactEdited({ type: 'custom/object', reference: 'new' })} type="button">
          Edit artifact
        </button>
        <button onClick={() => onExpectedArtifactSelected({ id: 'expected-id' })} type="button">
          Select expected artifact
        </button>
      </div>
    ),
  };
});

describe('ServerGroupImageSettings', () => {
  it('renders accessible image and source controls while preserving unavailable persisted values', () => {
    const values = command({
      backingData: { allImages: [{ imageName: 'known-image' }, { imageName: 'known-image' }] },
    });
    const { setFieldValue } = renderImageSettings(values);

    expect(selectOptions('Image source')).toEqual([
      ['artifact', 'Artifact'],
      ['priorStage', 'Prior Stage'],
      ['persisted-source', 'persisted-source (unavailable)'],
    ]);
    expect(selectOptions('Image')).toEqual([
      ['', 'Select...'],
      ['known-image', 'known-image'],
      ['persisted-image', 'persisted-image (unavailable)'],
    ]);
    expect(setFieldValue).not.toHaveBeenCalled();
  });

  it('updates image source and image selection as page-owned fields', () => {
    const { setFieldValue } = renderImageSettings(command());

    fireEvent.change(screen.getByLabelText('Image source'), { target: { value: 'priorStage' } });
    fireEvent.change(screen.getByLabelText('Image'), { target: { value: 'known-image' } });

    expect(setFieldValue.mock.calls).toEqual([
      ['imageSource', 'priorStage'],
      ['image', 'known-image'],
    ]);
  });

  it('shows configured image source text instead of an editable source control', () => {
    const values = command({
      imageSource: 'artifact',
      viewState: { mode: 'editPipeline', showImageSourceSelector: true, imageSourceText: 'From **trigger**' },
    });
    renderImageSettings(values);

    expect(screen.queryByLabelText('Image source')).not.toBeInTheDocument();
    expect(screen.getByText((_, element) => element?.textContent === 'From trigger')).toBeInTheDocument();
    expect(screen.getByTestId('artifact-selector')).toBeInTheDocument();
  });

  it('edits inline and expected image artifacts without retaining the other reference', () => {
    const values = command({
      imageSource: 'artifact',
      imageArtifactId: 'old-id',
      imageArtifact: { type: 'custom/object', reference: 'old' },
    });
    const { setFieldValue } = renderImageSettings(values);

    fireEvent.click(screen.getByRole('button', { name: 'Edit artifact' }));
    fireEvent.click(screen.getByRole('button', { name: 'Select expected artifact' }));

    expect(setFieldValue.mock.calls).toEqual([
      ['imageArtifactId', null],
      ['imageArtifact', { type: 'custom/object', reference: 'new' }],
      ['imageArtifactId', 'expected-id'],
      ['imageArtifact', null],
    ]);
  });

  it('does not render or require image selection when it is disabled', () => {
    const values = command({ image: null, viewState: { mode: 'editPipeline', disableImageSelection: true } });
    renderImageSettings(values);

    expect(screen.queryByLabelText('Image')).not.toBeInTheDocument();
    expect(screen.getByText('Image selection is disabled for this command.')).toBeInTheDocument();
    expect(validateGceServerGroupImageSettings(values)).toEqual({});
  });

  it('requires an image when selection is enabled', () => {
    expect(validateGceServerGroupImageSettings(command({ image: '' }))).toEqual({ image: 'Image required.' });
  });

  it('renders image validation next to the required control with an accessible description', () => {
    renderImageSettings(command({ image: '' }));
    const image = screen.getByLabelText('Image');
    const alert = screen.getByRole('alert');

    expect(image).toBeRequired();
    expect(image).toHaveAttribute('aria-invalid', 'true');
    expect(image).toHaveAttribute('aria-describedby', 'gce-server-group-image-error');
    expect(alert).toHaveAttribute('id', 'gce-server-group-image-error');
    expect(alert).toHaveTextContent('Image required.');
  });
});

function renderImageSettings(values: IGceServerGroupCommand) {
  const setFieldValue = vi.fn();
  render(
    <ServerGroupImageSettings
      app={{ name: 'app' } as any}
      formik={{ errors: {}, setFieldValue, setValues: vi.fn(), values } as any}
    />,
  );
  return { setFieldValue };
}

function selectOptions(label: string): string[][] {
  return within(screen.getByLabelText(label))
    .getAllByRole('option')
    .map((option) => [(option as HTMLOptionElement).value, option.textContent || '']);
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
