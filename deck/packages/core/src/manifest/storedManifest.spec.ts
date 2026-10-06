import { isStoredManifestReference, resolveStoredManifests } from './storedManifest';
import { ArtifactService } from '../pipeline/config/triggers/artifacts/ArtifactService';

const encode = (text: string) => btoa(unescape(encodeURIComponent(text)));

describe('storedManifest', () => {
  const reference = { type: 'remote/map/base64', reference: 'ref://app/abc123', name: 'stored-entity' };

  describe('isStoredManifestReference', () => {
    it('matches entity-store references only', () => {
      expect(isStoredManifestReference(reference)).toBe(true);
      expect(isStoredManifestReference({ kind: 'Deployment' })).toBe(false);
      expect(isStoredManifestReference(null)).toBe(false);
    });
  });

  describe('resolveStoredManifests', () => {
    it('replaces references with the stored manifest and leaves real manifests alone', async () => {
      const stored = { kind: 'ConfigMap', metadata: { name: 'cm' } };
      const spy = spyOn(ArtifactService, 'getArtifactByContentReference').and.returnValue(
        Promise.resolve({ reference: encode(JSON.stringify(stored)) }),
      );
      const inline = { kind: 'Deployment' };

      const result = await resolveStoredManifests([reference, inline]);

      expect(spy).toHaveBeenCalledWith('app/abc123');
      expect(result).toEqual([stored, inline]);
    });

    it('parses YAML content', async () => {
      spyOn(ArtifactService, 'getArtifactByContentReference').and.returnValue(
        Promise.resolve({ reference: encode('kind: ConfigMap\nmetadata:\n  name: cm\n') }),
      );
      expect(await resolveStoredManifests([reference])).toEqual([{ kind: 'ConfigMap', metadata: { name: 'cm' } }]);
    });

    it('keeps the reference when it cannot be fetched', async () => {
      spyOn(ArtifactService, 'getArtifactByContentReference').and.returnValue(Promise.reject('nope'));
      expect(await resolveStoredManifests([reference])).toEqual([reference]);
    });
  });
});
