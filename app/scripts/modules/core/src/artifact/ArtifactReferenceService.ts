import { get, noop, set } from 'lodash';

import { IStage } from '../domain';
import { Registry } from '../registry';

export class ArtifactReferenceService {
  /**
   * Removes an artifact reference from each of a list of stages with a
   * registered `artifactRemover` method.
   *
   * @param reference The ID of the artifact to remove.
   * @param stages The stages from which to remove artifact references.
   */

  public static removeReferenceFromStages(reference: string, stages: IStage[]): void {
    (stages || []).forEach((stage) => {
      const stageConfig = Registry.pipeline.getStageConfig(stage);
      const artifactRemover = get(stageConfig, ['artifactRemover'], noop);
      artifactRemover(stage, reference);
    });
  }

  /**
   * Removes each of a list of artifact references from each of a list of
   * stages with a registered `artifactRemover` method.
   *
   * @param references The list of artifact IDs to remove.
   * @param stages The stages from which to remove artifact references.
   */
  public static removeReferencesFromStages(references: string[], stages: IStage[]): void {
    references.forEach((reference) => ArtifactReferenceService.removeReferenceFromStages(reference, stages));
  }

  /**
   * Removes an artifact reference from a field of an object. The field can
   * be either a single artifact ID or a list of artifact IDs.
   *
   * @param path The path to the field from which to remove an artifact
   * reference. A nested path (e.g., `keyA.keyB`) is valid.
   * @param obj The object from which to remove the artifact reference at a
   * given field.
   * @param artifactId The ID of the artifact to remove.
   */
  public static removeArtifactFromField(
    path: string,
    obj: { [key: string]: string | string[] },
    artifactId: string,
  ): void {
    const reference: string | string[] = get(obj, path);
    if (Array.isArray(reference)) {
      set(
        obj,
        path,
        reference.filter((a: string) => a !== artifactId),
      );
    } else if (reference === artifactId) {
      set(obj, path, null);
    }
  }

  /**
   * Given a list of paths, returns a callback that removes an artifact
   * reference from a stage at those paths. This callback can be registered as
   * the `artifactRemover` of an {IStageTypeConfig}.
   *
   * @param paths The paths to the fields from which to remove an artifact.
   * Nested paths (e.g., `keyA.keyB`) are valid.
   * @returns A function that removes references to a given `artifactId` from
   * a given `stage` at each of the specified paths.
   */
  public static removeArtifactFromFields(paths: string[]): (stage: IStage, artifactId: string) => void {
    return (stage: IStage, artifactId: string) =>
      paths.forEach((path) => this.removeArtifactFromField(path, stage, artifactId));
  }
}
