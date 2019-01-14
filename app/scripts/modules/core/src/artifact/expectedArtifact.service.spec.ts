import { noop } from 'lodash';
import { ExpectedArtifactService } from './expectedArtifact.service';
import { IArtifact, IArtifactKindConfig } from 'core/domain';
import { Registry } from 'core/registry';

describe('ExpectedArtifactService', () => {
  describe('getKindConfig()', () => {
    const baseKindConfig = {
      label: '',
      description: '',
      isDefault: false,
      isMatch: false,
      template: '',
      controller: noop,
    };
    const kindConfigs: IArtifactKindConfig[] = [
      {
        type: 'foo-type',
        key: 'foo',
        isMatch: true,
      },
      {
        type: 'foo-type',
        key: 'foo-default',
        isDefault: true,
      },
      {
        type: 'bar-type',
        key: 'bar',
        isMatch: true,
      },
      {
        type: 'bar-type',
        key: 'bar-default',
        isDefault: true,
      },
    ].map(k => ({ ...baseKindConfig, ...k }));
    const customKindConfig = {
      ...baseKindConfig,
      key: 'custom',
      customKind: true,
      isMatch: true,
      isDefault: true,
    };
    beforeAll(() => {
      kindConfigs.forEach(kindConfig => Registry.pipeline.registerArtifactKind(kindConfig));
      Registry.pipeline.registerCustomArtifactKind(customKindConfig);
    });

    it('returns the custom kind when set as the artifact kind', () => {
      const artifact: IArtifact = {
        kind: 'custom',
        id: 'artifact-id',
      };
      const kindConfig = ExpectedArtifactService.getKindConfig(artifact, false);
      expect(kindConfig).toEqual(customKindConfig);
    });

    it('returns the custom kind when set as the artifact kind and isDefault is true', () => {
      const artifact: IArtifact = {
        kind: 'custom',
        id: 'artifact-id',
      };
      const kindConfig = ExpectedArtifactService.getKindConfig(artifact, true);
      expect(kindConfig).toEqual(customKindConfig);
    });

    it('infers kind from type', () => {
      const artifact: IArtifact = {
        id: 'artifact-id',
        type: 'bar-type',
      };
      const kindConfig = ExpectedArtifactService.getKindConfig(artifact, false);
      expect(kindConfig).toEqual(kindConfigs[2]);
    });

    it('infers kind from type when isDefault is true', () => {
      const artifact: IArtifact = {
        id: 'artifact-id',
        type: 'bar-type',
      };
      const kindConfig = ExpectedArtifactService.getKindConfig(artifact, true);
      expect(kindConfig).toEqual(kindConfigs[3]);
    });

    it('returns the custom kind when customKind is true, regardless of type', () => {
      const artifact: IArtifact = {
        id: 'artifact-id',
        customKind: true,
        type: 'bar-type',
      };
      const kindConfig = ExpectedArtifactService.getKindConfig(artifact, false);
      expect(kindConfig).toEqual(customKindConfig);
    });

    it('returns the custom kind when customKind is true, regardless of type, when isDefault is true', () => {
      const artifact: IArtifact = {
        id: 'artifact-id',
        customKind: true,
        type: 'bar-type',
      };
      const kindConfig = ExpectedArtifactService.getKindConfig(artifact, true);
      expect(kindConfig).toEqual(customKindConfig);
    });

    it('returns the default kind if neither kind nor type are stored on artifact', () => {
      const artifact: IArtifact = {
        id: 'artifact-id',
      };
      const kindConfig = ExpectedArtifactService.getKindConfig(artifact, false);
      expect(kindConfig).toEqual(customKindConfig);
    });

    it('returns the default kind if neither kind nor type are stored on artifact when isDefault is true', () => {
      const artifact: IArtifact = {
        id: 'artifact-id',
      };
      const kindConfig = ExpectedArtifactService.getKindConfig(artifact, true);
      expect(kindConfig).toEqual(customKindConfig);
    });
  });
});
