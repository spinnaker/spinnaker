import { IStage } from 'core/domain';
import { get, noop, set } from 'lodash';
import { Registry } from 'core/registry';

export class ArtifactReferenceService {
  public static removeReferenceFromStages(reference: string, stages: IStage[]) {
    (stages || []).forEach(stage => {
      const stageConfig = Registry.pipeline.getStageConfig(stage);
      const artifactRemover = get(stageConfig, ['artifactRemover'], noop);
      artifactRemover(stage, reference);
    });
  }

  public static removeArtifactFromField(field: string, obj: { [key: string]: string | string[] }, artifactId: string) {
    const reference = get(obj, field);
    if (Array.isArray(reference)) {
      set(obj, field, reference.filter((a: string) => a !== artifactId));
    } else if (reference === artifactId) {
      set(obj, field, null);
    }
  }

  public static removeArtifactFromFields(fields: string[]): (stage: IStage, artifactId: string) => void {
    return (stage: IStage, artifactId: string) =>
      fields.forEach(field => this.removeArtifactFromField(field, stage, artifactId));
  }
}
