import { noop } from 'lodash';
import { ExpectedArtifactService } from './expectedArtifact.service';
import { IArtifact } from 'core/domain';
import { Registry } from 'core/registry';

describe('ExpectedArtifactService', () => {
  describe('getKind()', () => {
    it('returns the kind stored on an artifact', () => {
      const expectedKind = 'foo';
      const artifact: IArtifact = {
        kind: expectedKind,
        id: 'artifact-id',
      };
      const kind = ExpectedArtifactService.getKind(artifact);
      expect(kind).toEqual(expectedKind);
    });

    it('infers kind from type if no explicit kind is stored on the artifact', () => {
      const expectedKind = 'my-custom-kind';
      Registry.pipeline.registerArtifactKind({
        label: 'foo',
        type: 'my-custom-type',
        description: 'foo',
        key: expectedKind,
        isDefault: false,
        isMatch: false,
        template: '',
        controller: noop,
      });
      const artifact: IArtifact = {
        id: 'artifact-id',
        type: 'my-custom-type',
      };
      const kind = ExpectedArtifactService.getKind(artifact);
      expect(kind).toEqual(expectedKind);
    });

    it('returns null kind if neither kind nor type are stored on artifact', () => {
      const expectedKind: string = null;
      const artifact: IArtifact = {
        id: 'artifact-id',
      };
      const kind = ExpectedArtifactService.getKind(artifact);
      expect(kind).toEqual(expectedKind);
    });
  });
});
