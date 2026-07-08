import { shallow } from 'enzyme';
import React from 'react';

import {
  AuthenticationService,
  BakeExecutionLabel,
  BakeryReader,
  ExecutionDetailsTasks,
  Registry,
  SETTINGS,
  Spinner,
} from '@spinnaker/core';

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

  it('registers a package-local React stage config without Angular templates', () => {
    Registry.pipeline.registerStage(DOCKER_BAKE_STAGE_CONFIG);

    const stageConfig = Registry.pipeline.getStageConfig({ type: 'bake', cloudProvider: 'docker' } as any);

    expect(stageConfig.provides).toBe('bake');
    expect(stageConfig.cloudProvider).toBe('docker');
    expect(stageConfig.component).toBe(DockerBakeStageConfig);
    expect(stageConfig.executionDetailsSections).toEqual([DockerBakeExecutionDetails, ExecutionDetailsTasks]);
    expect(stageConfig.executionLabelComponent).toBe(BakeExecutionLabel);
    expect(stageConfig.templateUrl).toBeUndefined();
    expect(stageConfig.executionDetailsUrl).toBeUndefined();
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
    spyOn(AuthenticationService, 'getAuthenticatedUser').and.returnValue({ name: 'user@example.com' } as any);
    spyOn(BakeryReader, 'getBaseOsOptions').and.returnValue(Promise.resolve({ baseImages: [{ id: 'ubuntu' }] } as any));
    spyOn(BakeryReader, 'getBaseLabelOptions').and.returnValue(Promise.resolve(['release']));

    const updateStage = jasmine.createSpy('updateStage');

    shallow(
      <DockerBakeStageConfig
        application={{} as any}
        pipeline={{} as any}
        stage={{ package: 'my-package', organization: '' } as any}
        stageFieldUpdated={jasmine.createSpy('stageFieldUpdated')}
        updateStage={updateStage}
        updateStageField={jasmine.createSpy('updateStageField')}
      />,
    );

    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(updateStage).toHaveBeenCalledWith({
      package: 'my-package',
      region: 'global',
      user: 'user@example.com',
      baseOs: 'ubuntu',
      baseLabel: 'release',
    });
  });

  it('shows an error instead of a permanent spinner when bake options fail to load', async () => {
    spyOn(BakeryReader, 'getBaseOsOptions').and.returnValue(Promise.reject(new Error('boom')));
    spyOn(BakeryReader, 'getBaseLabelOptions').and.returnValue(Promise.resolve(['release']));

    const wrapper = shallow(
      <DockerBakeStageConfig
        application={{} as any}
        pipeline={{} as any}
        stage={{ package: 'my-package' } as any}
        stageFieldUpdated={jasmine.createSpy('stageFieldUpdated')}
        updateStage={jasmine.createSpy('updateStage')}
        updateStageField={jasmine.createSpy('updateStageField')}
      />,
    );

    await new Promise((resolve) => setTimeout(resolve, 0));
    wrapper.update();

    expect(wrapper.find(Spinner).exists()).toBe(false);
    expect(wrapper.text()).toContain('Unable to load Docker bake options');
  });

  it('does not update state after unmounting before bake options load', async () => {
    let resolveBaseOsOptions: (value: any) => void;
    let resolveBaseLabelOptions: (value: string[]) => void;
    spyOn(BakeryReader, 'getBaseOsOptions').and.returnValue(new Promise((resolve) => (resolveBaseOsOptions = resolve)));
    spyOn(BakeryReader, 'getBaseLabelOptions').and.returnValue(
      new Promise((resolve) => (resolveBaseLabelOptions = resolve)),
    );

    const wrapper = shallow(
      <DockerBakeStageConfig
        application={{} as any}
        pipeline={{} as any}
        stage={{ package: 'my-package' } as any}
        stageFieldUpdated={jasmine.createSpy('stageFieldUpdated')}
        updateStage={jasmine.createSpy('updateStage')}
        updateStageField={jasmine.createSpy('updateStageField')}
      />,
    );
    const setState = spyOn(wrapper.instance() as DockerBakeStageConfig, 'setState');

    wrapper.unmount();
    resolveBaseOsOptions!({ baseImages: [{ id: 'ubuntu' }] });
    resolveBaseLabelOptions!(['release']);
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(setState).not.toHaveBeenCalled();
  });

  it('replaces every bakery detail URL placeholder occurrence', () => {
    SETTINGS.bakeryDetailUrl =
      '/bakery/{{context.region}}/{{context.region}}/{{context.status.resourceId}}/{{context.status.resourceId}}';

    const wrapper = shallow(
      <DockerBakeExecutionDetails
        current={true}
        name="bakeConfig"
        stage={
          {
            context: { region: 'us-west-2', status: { resourceId: 'image-123' } },
          } as any
        }
      />,
    );

    expect(wrapper.find('a').prop('href')).toBe('/bakery/us-west-2/us-west-2/image-123/image-123');
  });
});
