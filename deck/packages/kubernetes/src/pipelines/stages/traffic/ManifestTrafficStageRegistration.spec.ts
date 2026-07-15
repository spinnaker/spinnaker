import { Registry } from '@spinnaker/core';

import { DISABLE_MANIFEST_STAGE_CONFIG } from './disableManifest.stage';
import { ENABLE_MANIFEST_STAGE_CONFIG } from './enableManifest.stage';
import { ManifestTrafficStageConfig } from './ManifestTrafficStageConfig';

describe('ManifestTraffic stage registration', () => {
  beforeEach(() => Registry.reinitialize());
  afterEach(() => Registry.reinitialize());

  it('registers enable and disable manifest stage configs without Angular module config', () => {
    Registry.pipeline.registerStage(ENABLE_MANIFEST_STAGE_CONFIG);
    Registry.pipeline.registerStage(DISABLE_MANIFEST_STAGE_CONFIG);

    const enableStage = Registry.pipeline.getStageConfig({ type: 'enableManifest' } as any);
    const disableStage = Registry.pipeline.getStageConfig({ type: 'disableManifest' } as any);

    expect(ENABLE_MANIFEST_STAGE_CONFIG.key).toBe('enableManifest');
    expect(enableStage.label).toBe('Enable (Manifest)');
    expect(enableStage.description).toBe('Enable a Kubernetes manifest.');
    expect(enableStage.cloudProvider).toBe('kubernetes');
    expect(enableStage.component).toBe(ManifestTrafficStageConfig);
    expect(enableStage.accountExtractor({ account: 'k8s-local' } as any)).toEqual(['k8s-local']);
    expect(enableStage.configAccountExtractor({ account: 'k8s-local' } as any)).toEqual(['k8s-local']);

    expect(DISABLE_MANIFEST_STAGE_CONFIG.key).toBe('disableManifest');
    expect(disableStage.label).toBe('Disable (Manifest)');
    expect(disableStage.description).toBe('Disable a Kubernetes manifest.');
    expect(disableStage.cloudProvider).toBe('kubernetes');
    expect(disableStage.component).toBe(ManifestTrafficStageConfig);
    expect(disableStage.accountExtractor({ account: 'k8s-local' } as any)).toEqual(['k8s-local']);
    expect(disableStage.configAccountExtractor({ account: 'k8s-local' } as any)).toEqual(['k8s-local']);
  });
});
