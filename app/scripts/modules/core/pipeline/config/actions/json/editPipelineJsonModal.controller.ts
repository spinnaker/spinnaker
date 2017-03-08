import {cloneDeepWith} from 'lodash';
import {extend, module, IComponentController} from 'angular';
import {IModalServiceInstance} from 'angular-ui-bootstrap';

import {JSON_UTILITY_SERVICE, JsonUtilityService} from 'core/utils/json/json.utility.service';
import {IPipeline, IStage} from 'core/domain';

interface ICommand {
  errorMessage?: string;
  invalid?: boolean;
  pipelineJSON: string;
  locked: boolean;
}

export class EditPipelineJsonModalCtrl implements IComponentController {

  public isStrategy: boolean;
  public command: ICommand;

  static get $inject(): string[] {
    return ['$uibModalInstance', 'jsonUtilityService', 'pipeline'];
  }

  constructor(private $uibModalInstance: IModalServiceInstance,
              private jsonUtilityService: JsonUtilityService,
              private pipeline: IPipeline) {}

  private removeImmutableFields(pipeline: IPipeline): void {
    delete pipeline.name;
    delete pipeline.application;
    delete pipeline.index;
    delete pipeline.id;
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
    const copy = cloneDeepWith<IPipeline>(this.pipeline, (value: any) => {
      if (value && value.$$hashKey) {
        delete value.$$hashKey;
      }
      return undefined; // required for clone operation and typescript happiness
    });
    this.removeImmutableFields(copy);

    this.isStrategy = this.pipeline.strategy || false;
    this.command = {
      pipelineJSON: this.jsonUtilityService.makeSortedStringFromObject(copy),
      locked: copy.locked
    };
  }

  public updatePipeline(): void {
    try {
      const parsed = JSON.parse(this.command.pipelineJSON);
      parsed.appConfig = parsed.appConfig || {};

      this.validatePipeline(parsed);

      this.removeImmutableFields(parsed);
      extend(this.pipeline, parsed);
      this.$uibModalInstance.close();
    } catch (e) {
      this.command.invalid = true;
      this.command.errorMessage = e.message;
    }
  }
}

export const EDIT_PIPELINE_JSON_MODAL_CONTROLLER = 'spinnaker.core.pipeline.config.actions.editJson';
module(EDIT_PIPELINE_JSON_MODAL_CONTROLLER, [JSON_UTILITY_SERVICE]);
