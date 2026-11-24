import { render, screen, waitFor } from '@testing-library/react';
import React from 'react';

import {
  AuthenticationService,
  BakeExecutionLabel,
  BakeryReader,
  ExecutionDetailsTasks,
  Registry,
  SETTINGS,
} from '@spinnaker/core';

import { renderWithRouter } from '../../../../../core/src/utils/testUtils/rtl';

import {
  applyDockerBakeStageDefaults,
  DockerBakeExecutionDetails,
  DockerBakeStageConfig,
  DOCKER_BAKE_STAGE_CONFIG,
} from './dockerBakeStage';

describe('Docker bake stage', () => {
  let originalBakeryDetailUrl: string;

  beforeEach(() => {
    originalBakeryDetailUrl = SETTINGS.bakeryDetailUrl;
  });

  beforeEach(() => Registry.reinitialize());
  afterEach(() => {
    SETTINGS.bakeryDetailUrl = originalBakeryDetailUrl;
    Registry.reinitialize();
  });

  it('registers the package-local React stage config', () => {
    Registry.pipeline.registerStage(DOCKER_BAKE_STAGE_CONFIG);

    const stageConfig = Registry.pipeline.getStageConfig({ type: 'bake', cloudProvider: 'docker' } as any);

    expect(stageConfig.provides).toBe('bake');
    expect(stageConfig.cloudProvider).toBe('docker');
    expect(stageConfig.component).toBe(DockerBakeStageConfig);
    expect(stageConfig.executionDetailsSections).toEqual([DockerBakeExecutionDetails, ExecutionDetailsTasks]);
    expect(stageConfig.executionLabelComponent).toBe(BakeExecutionLabel);
  });

  it('applies Docker bake defaults and removes empty string properties', () => {
    const stage: any = {
      package: 'my-package',
      organization: '',
      extendedAttributes: {},
    };

    const result = applyDockerBakeStageDefaults(stage, {
      user: 'user@example.com',
      baseOsOptions: [{ id: 'ubuntu' }, { id: 'debian' }],
      baseLabelOptions: ['release', 'snapshot'],
    });

    expect(result).toEqual({
      package: 'my-package',
      extendedAttributes: {},
      region: 'global',
      user: 'user@example.com',
      baseOs: 'ubuntu',
      baseLabel: 'release',
    });
    expect(result).not.toBe(stage);
  });

  it('keeps existing Docker bake values when defaults are available', () => {
    const stage: any = {
      region: 'custom-region',
      user: 'existing-user',
      baseOs: 'debian',
      baseLabel: 'snapshot',
    };

    const result = applyDockerBakeStageDefaults(stage, {
      user: 'user@example.com',
      baseOsOptions: [{ id: 'ubuntu' }],
      baseLabelOptions: ['release'],
    });

    expect(result.region).toBe('custom-region');
    expect(result.user).toBe('existing-user');
    expect(result.baseOs).toBe('debian');
    expect(result.baseLabel).toBe('snapshot');
  });

  it('persists Docker bake defaults after loading options', async () => {
    vi.spyOn(AuthenticationService, 'getAuthenticatedUser').mockReturnValue({ name: 'user@example.com' } as any);
    vi.spyOn(BakeryReader, 'getBaseOsOptions').mockReturnValue(
      Promise.resolve({ baseImages: [{ id: 'ubuntu' }] } as any),
    );
    vi.spyOn(BakeryReader, 'getBaseLabelOptions').mockReturnValue(Promise.resolve(['release']));

    const updateStage = vi.fn();

    render(
      <DockerBakeStageConfig
        application={{} as any}
        pipeline={{} as any}
        stage={{ package: 'my-package', organization: '' } as any}
        stageFieldUpdated={vi.fn()}
        updateStage={updateStage}
        updateStageField={vi.fn()}
      />,
    );

    await waitFor(() =>
      expect(updateStage).toHaveBeenCalledWith({
        package: 'my-package',
        region: 'global',
        user: 'user@example.com',
        baseOs: 'ubuntu',
        baseLabel: 'release',
      }),
    );
  });

  it('shows an error instead of a permanent spinner when bake options fail to load', async () => {
    vi.spyOn(BakeryReader, 'getBaseOsOptions').mockReturnValue(Promise.reject(new Error('boom')));
    vi.spyOn(BakeryReader, 'getBaseLabelOptions').mockReturnValue(Promise.resolve(['release']));

    render(
      <DockerBakeStageConfig
        application={{} as any}
        pipeline={{} as any}
        stage={{ package: 'my-package' } as any}
        stageFieldUpdated={vi.fn()}
        updateStage={vi.fn()}
        updateStageField={vi.fn()}
      />,
    );

    expect(await screen.findByText('Unable to load Docker bake options.')).toBeInTheDocument();
  });

  it('does not update state after unmounting before bake options load', async () => {
    let resolveBaseOsOptions: (value: any) => void;
    let resolveBaseLabelOptions: (value: string[]) => void;
    vi.spyOn(BakeryReader, 'getBaseOsOptions').mockReturnValue(
      new Promise((resolve) => (resolveBaseOsOptions = resolve)),
    );
    vi.spyOn(BakeryReader, 'getBaseLabelOptions').mockReturnValue(
      new Promise((resolve) => (resolveBaseLabelOptions = resolve)),
    );

    const updateStage = vi.fn();
    const rendered = render(
      <DockerBakeStageConfig
        application={{} as any}
        pipeline={{} as any}
        stage={{ package: 'my-package' } as any}
        stageFieldUpdated={vi.fn()}
        updateStage={updateStage}
        updateStageField={vi.fn()}
      />,
    );
    rendered.unmount();
    resolveBaseOsOptions!({ baseImages: [{ id: 'ubuntu' }] });
    resolveBaseLabelOptions!(['release']);
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(updateStage).not.toHaveBeenCalled();
  });

  it('replaces every bakery detail URL placeholder occurrence', () => {
    SETTINGS.bakeryDetailUrl =
      '/bakery/{{context.region}}/{{context.region}}/{{context.status.resourceId}}/{{context.status.resourceId}}';

    renderWithRouter(
      <DockerBakeExecutionDetails
        current="bakeConfig"
        name="bakeConfig"
        stage={
          {
            context: { region: 'us-west-2', status: { resourceId: 'image-123' } },
          } as any
        }
      />,
    );

    expect(screen.getByRole('link', { name: 'View Bakery Details' })).toHaveAttribute(
      'href',
      '/bakery/us-west-2/us-west-2/image-123/image-123',
    );
  });
});
