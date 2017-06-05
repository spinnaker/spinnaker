import { module, IComponentController, IScope, IHttpPromiseCallbackArg, IPromise } from 'angular';
import { IModalInstanceService } from 'angular-ui-bootstrap';
import { load, dump } from 'js-yaml';
import autoBindMethods from 'class-autobind-decorator';
import { without, chain, has } from 'lodash';
import {
  PIPELINE_TEMPLATE_SERVICE,
  IVariableMetadata, IPipelineTemplateConfig, IPipelineTemplatePlanResponse, IPipelineTemplate,
  IPipelineTemplatePlanError
} from './pipelineTemplate.service';
import { IVariable } from './inputs/variableInput.service';
import { Application } from 'core/application/application.model';
import { ReactInjector } from 'core/reactShims';

export interface IVariableMetadataGroup {
  name: string;
  variableMetadata: IVariableMetadata[];
}

@autoBindMethods
export class ConfigurePipelineTemplateModalController implements IComponentController {

  public pipelineName: string;
  public variableMetadataGroups: IVariableMetadataGroup[];
  public variables: IVariable[];
  public state = {loading: true, error: false, errorMessage: '', noVariables: false};
  private template: IPipelineTemplate;
  private source: string;

  constructor(private $scope: IScope, private $uibModalInstance: IModalInstanceService,
              private application: Application, public pipelineTemplateConfig: IPipelineTemplateConfig,
              public isNew: boolean, private pipelineId: string) {
    'ngInject';
  }

  public $onInit(): void {
    this.initialize();
  }

  public initialize(): void {
    this.pipelineName = this.pipelineTemplateConfig.config.pipeline.name;
    this.source = this.pipelineTemplateConfig.config.pipeline.template.source;
    this.loadTemplate()
      .then(() => {
        this.groupVariableMetadata();
        this.initializeVariables();
        if (!this.variables.length) {
          this.state.noVariables = true;
        }
      })
      .then(() => this.state.loading = false)
      .catch(() => {
        Object.assign(this.state, {loading: false, error: true, errorMessage: 'Could not load pipeline template.'});
      });
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

  public formIsValid(): boolean {
    return this.variables.every(v => v.errors.length === 0);
  }

  public submit(): IPromise<void> {
    const config = this.buildConfig();
    return ReactInjector.pipelineTemplateService.getPipelinePlan(config)
      .then(plan => {
        this.$uibModalInstance.close({plan, config});
      })
      .catch((response: IHttpPromiseCallbackArg<IPipelineTemplatePlanResponse>) => {
        let errorMessage: string;
        if (has(response, 'data.errors.length')) {
          errorMessage = response.data.errors.map(this.renderError).join('\n');
        } else {
          errorMessage = 'No message provided';
        }
        Object.assign(this.state, {loading: false, error: true, errorMessage});
      });
  }

  private renderError(e: IPipelineTemplatePlanError): string {
    const messages = [];
    if (e.severity) {
      messages.push(`Severity: ${e.severity}`);
    }
    if (e.message) {
      messages.push(`Message: ${e.message}`);
    }
    if (e.location) {
      messages.push(`Location: ${e.location}`);
    }
    if (e.cause) {
      messages.push(`Cause: ${e.cause}`);
    }
    if (e.suggestion) {
      messages.push(`Suggestion: ${e.suggestion}`);
    }
    if (e.details) {
      messages.push(`Details: ${e.details}`);
    }
    if (e.nestedErrors) {
      messages.push(`Additional Errors: ${e.nestedErrors.map(nestedError => `${nestedError} <br>`)}`);
    }
    return `<ul>${messages.map(m => `<li>${m}</li>`).join('\n')}</ul>`;
  }

  public dismissError(): void {
    Object.assign(this.state, {error: false, errorMessage: null});
  }

  public buildConfig(): IPipelineTemplateConfig {
    return Object.assign(
      this.pipelineTemplateConfig || {},
      {
        type: 'templatedPipeline',
        name: this.pipelineName,
        application: this.application.name,
        config: {
          schema: '1',
          pipeline: {
            name: this.pipelineName,
            application: this.application.name,
            pipelineConfigId: this.pipelineId,
            template: {source: this.source},
            variables: this.transformVariablesForPipelinePlan(),
          }
        }
      }
    );
  }

  private loadTemplate(): IPromise<void> {
    return ReactInjector.pipelineTemplateService.getPipelineTemplateFromSourceUrl(this.source)
      .then(template => { this.template = template });
  }

  private transformVariablesForPipelinePlan(): { [key: string]: any } {
    return chain(this.variables || [])
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
    (this.template.variables || []).forEach(v => {
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
    this.variables = (this.template.variables || []).map(v => {
      return {
        name: v.name,
        type: v.type || 'string',
        errors: [],
        value: this.getInitialVariableValue(v),
        hideErrors: true,
      };
    });
    this.variables.forEach(v => v.errors = ReactInjector.variableValidatorService.validate(v));
  }

  private getInitialVariableValue(variable: IVariableMetadata): any {
    if (has(this.pipelineTemplateConfig, 'config.pipeline.variables')) {
      let value = this.pipelineTemplateConfig.config.pipeline.variables[variable.name];
      if (variable.type === 'object' && value) {
        value = dump(value);
      }
      return value;
    } else {
      return (variable.type === 'list' && !variable.defaultValue) ? [''] : variable.defaultValue;
    }
  }
}

export const CONFIGURE_PIPELINE_TEMPLATE_MODAL_CTRL = 'spinnaker.core.pipeline.configureTemplate.modal.controller';
module(CONFIGURE_PIPELINE_TEMPLATE_MODAL_CTRL, [PIPELINE_TEMPLATE_SERVICE])
  .controller('ConfigurePipelineTemplateModalCtrl', ConfigurePipelineTemplateModalController);
