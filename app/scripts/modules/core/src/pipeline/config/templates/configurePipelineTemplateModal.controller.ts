import {module, IComponentController, IScope} from 'angular';
import {IModalInstanceService} from 'angular-ui-bootstrap';
import {load} from 'js-yaml';
import autoBindMethods from 'class-autobind-decorator';
import {without, chain} from 'lodash';
import {
  PIPELINE_TEMPLATE_SERVICE, IPipelineTemplate,
  IVariableMetadata, IPipelineConfig
} from './pipelineTemplate.service';
import {IVariable} from './inputs/variableInput.service';
import {Application} from 'core/application/application.model';
import { ReactInjector } from 'core/react';

export interface IVariableMetadataGroup {
  name: string;
  variableMetadata: IVariableMetadata[];
}

@autoBindMethods
export class ConfigurePipelineTemplateModalController implements IComponentController {

  public variableMetadataGroups: IVariableMetadataGroup[];

  constructor(private $scope: IScope, private $uibModalInstance: IModalInstanceService,
              private application: Application, private template: IPipelineTemplate, public pipelineName: string,
              public variables: IVariable[]) { 'ngInject' }

  public $onInit(): void {
    this.initialize();
  }

  public initialize(): void {
    this.groupVariableMetadata();
    this.initializeVariables();
  }

  public cancel(): void {
    this.$uibModalInstance.close();
  }

  public formIsValid(): boolean {
    return this.variables.every(v => v.errors.length === 0);
  }

  public getPipelineConfigPlan(): void {
    ReactInjector.pipelineTemplateService.getPipelinePlan(this.buildConfig())
      .then(() => { }).catch(() => { }); // TODO(dpeach)
  }

  public buildConfig(): IPipelineConfig {
    return {
      type: 'templatedPipeline',
      plan: true,
      config: {
        schema: '1',
        pipeline: {
          name: this.pipelineName,
          application: this.application.name,
          template: { source: null },
          variables: this.transformVariablesForPipelinePlan(),
        }
      }
    };
  }

  private transformVariablesForPipelinePlan(): {[key: string]: any} {
    return chain(this.variables)
      .cloneDeep()
      .map(v => {
        if (v.type === 'object') {
          v.value = load(v.value);
        }
        return [v.name, v.value];
      })
      .fromPairs()
      .value();
  }

  public handleVariableChange(newVariable: IVariable): void {
    const oldVariable = this.getVariable(newVariable.name);
    newVariable.errors = ReactInjector.variableValidatorService.validate(newVariable);
    this.variables = without(this.variables, oldVariable).concat([newVariable]);

    // `handleVariableChange` is passed to a React component, and Angular has no idea when it has been called.
    this.$scope.$digest();
  }

  private getVariable(name: string): IVariable {
    return this.variables.find(v => v.name === name);
  }

  private groupVariableMetadata(): void {
    this.variableMetadataGroups = [];
    this.template.variables.forEach(v => {
      if (v.group) {
        this.addToGroup(v.group, v);
      } else {
        this.addToGroup('Ungrouped', v);
      }
    });
  }

  private addToGroup(groupName: string, metadata: IVariableMetadata): void {
    const group = this.variableMetadataGroups.find(g => g.name === groupName);
    if (group) {
      group.variableMetadata.push(metadata);
    } else {
      this.variableMetadataGroups.push({name: groupName, variableMetadata: [metadata]});
    }
  }

  private initializeVariables(): void {
    if (!this.variables) {
      this.variables = this.template.variables.map(v => {
        const defaultValue = (v.type === 'list' && !v.defaultValue) ? [''] : v.defaultValue;
        return {
          name: v.name,
          type: v.type || 'string',
          errors: [],
          value: defaultValue,
          hideErrors: true,
        };
      });
    }
    this.variables.forEach(v => {
      v.errors = ReactInjector.variableValidatorService.validate(v);
    });
  }
}

export const CONFIGURE_PIPELINE_TEMPLATE_MODAL_CTRL = 'spinnaker.core.pipeline.configureTemplate.modal.controller';
module(CONFIGURE_PIPELINE_TEMPLATE_MODAL_CTRL, [PIPELINE_TEMPLATE_SERVICE])
  .controller('ConfigurePipelineTemplateModalCtrl', ConfigurePipelineTemplateModalController);
