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
    const defaultKindConfig = {
      ...baseKindConfig,
      key: 'custom',
      isMatch: true,
      isDefault: true,
    };
    beforeAll(() => {
      kindConfigs.forEach(kindConfig => Registry.pipeline.registerArtifactKind(kindConfig));
      Registry.pipeline.registerDefaultArtifactKind(defaultKindConfig);
    });

    it('returns the kind stored on an artifact', () => {
      const artifact: IArtifact = {
        kind: 'foo',
        id: 'artifact-id',
      };
      const kindConfig = ExpectedArtifactService.getKindConfig(artifact, false);
      expect(kindConfig).toEqual(kindConfigs[0]);
    });

    it('returns the kind stored on an artifact regardless of default setting', () => {
      const artifact: IArtifact = {
        kind: 'foo',
        id: 'artifact-id',
      };
      const kindConfig = ExpectedArtifactService.getKindConfig(artifact, true);
      expect(kindConfig).toEqual(kindConfigs[0]);
    });

    it('infers kind from type if no explicit kind is stored on the artifact', () => {
      const artifact: IArtifact = {
        id: 'artifact-id',
        type: 'bar-type',
      };
      const kindConfig = ExpectedArtifactService.getKindConfig(artifact, false);
      expect(kindConfig).toEqual(kindConfigs[2]);
    });

    it('infers kind from type if no explicit kind is stored on the artifact when isDefault is true', () => {
      const artifact: IArtifact = {
        id: 'artifact-id',
        type: 'bar-type',
      };
      const kindConfig = ExpectedArtifactService.getKindConfig(artifact, true);
      expect(kindConfig).toEqual(kindConfigs[3]);
    });

    it('returns the default kind if neither kind nor type are stored on artifact', () => {
      const artifact: IArtifact = {
        id: 'artifact-id',
      };
      const kindConfig = ExpectedArtifactService.getKindConfig(artifact, false);
      expect(kindConfig).toEqual(defaultKindConfig);
    });

    it('returns the default kind if neither kind nor type are stored on artifact when isDefault is true', () => {
      const artifact: IArtifact = {
        id: 'artifact-id',
      };
      const kindConfig = ExpectedArtifactService.getKindConfig(artifact, true);
      expect(kindConfig).toEqual(defaultKindConfig);
    });
  });
});
