import { IController } from 'angular';
import { cloneDeepWith } from 'lodash';
import { IModalServiceInstance } from 'angular-ui-bootstrap';

import { jsonUtilityService } from 'core/utils/json/json.utility.service';
import { IPipeline, IStage } from 'core/domain';

export interface IEditPipelineJsonModalCommand {
  errorMessage?: string;
  invalid?: boolean;
  pipelineJSON: string;
  pipelinePlanJSON?: string;
  locked: boolean;
}

export class EditPipelineJsonModalCtrl implements IController {

  public isStrategy: boolean;
  public command: IEditPipelineJsonModalCommand;
  public mode = 'pipeline'
  private immutableFields = ['name', 'application', 'index', 'id', '$$hashKey'];

  constructor(private $uibModalInstance: IModalServiceInstance,
              private pipeline: IPipeline, private plan?: IPipeline) {
    'ngInject';
  }

  private removeImmutableFields(pipeline: IPipeline): void {
    // no index signature on pipeline
    this.immutableFields.forEach(k => delete (pipeline as any)[k]);
  }

  private validatePipeline(pipeline: IPipeline): void {

    const refIds = new Set<string | number>();
    const badIds = new Set<string | number>();
    if (pipeline.stages) {
      pipeline.stages.forEach((stage: IStage) => {
        if (refIds.has(stage.refId)) {
          badIds.add(stage.refId);
        }
        refIds.add(stage.refId);
      });

      if (badIds.size) {
        throw new Error(`The refId property must be unique across stages.  Duplicate id(s): ${Array.from(badIds).toString()}`);
      }
    }
  }

  public $onInit(): void {
    const copy = this.clone(this.pipeline);
    let copyPlan: IPipeline;
    if (this.plan) {
      copyPlan = this.clone(this.plan);
    }

    this.isStrategy = this.pipeline.strategy || false;
    this.command = {
      pipelineJSON: jsonUtilityService.makeSortedStringFromObject(copy),
      pipelinePlanJSON: copyPlan ? jsonUtilityService.makeSortedStringFromObject(copyPlan) : null,
      locked: copy.locked
    };
  }

  private clone(pipeline: IPipeline): IPipeline {
    const copy = cloneDeepWith<IPipeline>(pipeline, (value: any) => {
      if (value && value.$$hashKey) {
        delete value.$$hashKey;
      }
      return undefined; // required for clone operation and typescript happiness
    });
    this.removeImmutableFields(copy);
    return copy;
  }

  public updatePipeline(): void {
    try {
      const parsed = JSON.parse(this.command.pipelineJSON);
      parsed.appConfig = parsed.appConfig || {};

      this.validatePipeline(parsed);

      Object.keys(this.pipeline)
        .filter(k => !this.immutableFields.includes(k) && !parsed.hasOwnProperty(k))
        .forEach(k => delete (this.pipeline as any)[k]);
      this.removeImmutableFields(parsed);
      Object.assign(this.pipeline, parsed);

      this.$uibModalInstance.close();
    } catch (e) {
      this.command.invalid = true;
      this.command.errorMessage = e.message;
    }
  }
}
