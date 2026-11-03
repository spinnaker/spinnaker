import { load } from 'js-yaml';

import { ArtifactService } from '../pipeline/config/triggers/artifacts/ArtifactService';
import { decodeUnicodeBase64 } from '../utils';

const STORED_MANIFEST_TYPE = 'remote/map/base64';

// A manifest that the artifact-store entity storage feature has replaced with a reference.
export interface IStoredManifestReference {
  type: string;
  reference: string;
  name?: string;
  metadata?: {
    kind?: string;
    name?: string;
    namespace?: string;
  };
}

export function isStoredManifestReference(manifest: unknown): manifest is IStoredManifestReference {
  return (
    !!manifest &&
    typeof manifest === 'object' &&
    (manifest as IStoredManifestReference).type === STORED_MANIFEST_TYPE &&
    typeof (manifest as IStoredManifestReference).reference === 'string'
  );
}

function parseManifest(text: string): any {
  try {
    return JSON.parse(text);
  } catch {
    return load(text);
  }
}

/**
 * Replaces any entity-store manifest references with the manifests they point to. A reference
 * that can't be fetched or parsed is left in place so that saving the stage doesn't lose it.
 */
export function resolveStoredManifests(manifests: any[]): Promise<any[]> {
  return Promise.all(
    manifests.map((manifest) => {
      if (!isStoredManifestReference(manifest)) {
        return manifest;
      }
      return ArtifactService.getArtifactByContentReference(manifest.reference.replace(/^ref?:\/\//, ''))
        .then((artifact) => parseManifest(decodeUnicodeBase64(artifact.reference)))
        .catch(() => manifest);
    }),
  );
}
