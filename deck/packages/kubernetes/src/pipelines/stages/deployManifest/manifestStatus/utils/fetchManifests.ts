import { from, of } from 'rxjs';
import { catchError, filter, map, mergeMap, toArray } from 'rxjs/operators';

import { ManifestReader, MAP_ARTIFACT_TYPE_REMOTE } from '@spinnaker/core';
import type { IArtifact } from '@spinnaker/core';

import type { IStageManifest } from '../../../../../manifest';

/**
 * Retrieves manifests.
 *
 * @param rawManifests
 * @returns A promise resolving to an array of IStageManifest objects.
 */
export const fetchManifests = async (rawManifests: any[]): Promise<IStageManifest[]> => {
  // Process each manifest
  return from(rawManifests)
    .pipe(
      mergeMap((item) => {
        const artifact = item as IArtifact;

        if (artifact.type === MAP_ARTIFACT_TYPE_REMOTE) {
          // Fetch and expand remote artifact manifests
          return from(ManifestReader.getManifestFromArtifact(artifact)).pipe(
            map((manifest) => (manifest as unknown) as IStageManifest),
            catchError((error) => {
              console.error(
                `Error fetching manifest: ${
                  typeof error === 'string' ? error : error?.data?.message ?? JSON.stringify(error)
                }`,
              );
              return of(null);
            }),
          );
        } else {
          // Include the manifest as is
          return of(item as IStageManifest);
        }
      }),
      filter((manifest): manifest is IStageManifest => manifest !== null),
      toArray(),
    )
    .toPromise();
};
