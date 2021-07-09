import { cloneDeepWith } from 'lodash';

import { IPipeline } from '../../../domain';

export class PipelineJSONService {
  // these fields are not user-editable, so we hide them
  public static immutableFields = new Set(['name', 'application', 'index', 'id', '$$hashKey']);

  private static removeImmutableFields(pipeline: IPipeline): void {
    // no index signature on pipeline
    PipelineJSONService.immutableFields.forEach((k) => delete (pipeline as any)[k]);
  }

  public static clone(pipeline: IPipeline): IPipeline {
    const copy = cloneDeepWith<IPipeline>(pipeline, (value: any) => {
      if (value && value.$$hashKey) {
        delete value.$$hashKey;
      }
      return undefined; // required for clone operation and typescript happiness
    });
    PipelineJSONService.removeImmutableFields(copy);
    return copy;
  }
}
