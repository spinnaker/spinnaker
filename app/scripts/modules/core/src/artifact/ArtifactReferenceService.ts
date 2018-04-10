import { module } from 'angular';
import { IStage } from 'core/domain';
import { isEmpty, get } from 'lodash';

export type SupportedStage = 'stage';

type IWalker = (refContainer: any) => Array<string | number>[];

interface IReference {
  category: SupportedStage;
  walker: IWalker;
}

export class ArtifactReferenceServiceProvider {
  private references: IReference[] = [];

  public $get() {
    return this;
  }

  public registerReference(category: SupportedStage, walker: any) {
    this.references.push({ category, walker });
  }

  public removeReferenceFromStages(reference: string, stages: IStage[]) {
    (stages || []).forEach(stage => {
      this.references.forEach(ref => {
        const paths: Array<string | number>[] = ref.walker(stage).filter(path => !isEmpty(path));
        paths.map(p => p.slice(0)).forEach(path => {
          let tail = path.pop();
          let obj = stage;
          if (path.length > 0) {
            obj = get(stage, path);
          }
          if (Array.isArray(obj[tail])) {
            obj = obj[tail];
            tail = obj.indexOf(reference);
          }
          if (obj[tail] !== reference) {
            return;
          }
          if (Array.isArray(obj)) {
            obj.splice(tail as number, 1);
          } else {
            delete obj[tail];
          }
        });
      });
    });
  }
}

export const ARTIFACT_REFERENCE_SERVICE_PROVIDER = 'spinnaker.core.artifacts.referenceServiceProvider';
module(ARTIFACT_REFERENCE_SERVICE_PROVIDER, []).provider('artifactReferenceService', ArtifactReferenceServiceProvider);
