import { IController, IHttpPromiseCallbackArg, IScope, module } from 'angular';
import { IModalInstanceService } from 'angular-ui-bootstrap';
import { dump, load } from 'js-yaml';
import { chain, has, without } from 'lodash';

import {
  IPipelineTemplate,
  IPipelineTemplateConfig,
  IPipelineTemplatePlanError,
  IPipelineTemplatePlanResponse,
  IVariableMetadata,
  PipelineTemplateReader,
} from './PipelineTemplateReader';
import { Application } from '../../../application/application.model';
import { IVariable } from './inputs/variableInput.service';
import { VariableValidatorService } from './validators/variableValidator.service';

export interface IVariableMetadataGroup {
  name: string;
  variableMetadata: IVariableMetadata[];
}

export interface IState {
  error: boolean;
  loading: boolean;
  loadingError: boolean;
  noVariables: boolean;
  planErrors: IPipelineTemplatePlanError[];
  inheritTemplateParameters: boolean;
  inheritTemplateTriggers: boolean;
  inheritTemplateExpectedArtifacts: boolean;
}

export class ConfigurePipelineTemplateModalController implements IController {
  public pipelineName: string;
  public variableMetadataGroups: IVariableMetadataGroup[];
  public variables: IVariable[];
  public state: IState = {
    loading: true,
    error: false,
    planErrors: null,
    loadingError: false,
    noVariables: false,
    inheritTemplateParameters: true,
    inheritTemplateTriggers: true,
    inheritTemplateExpectedArtifacts: true,
  };
  private template: IPipelineTemplate;
  private source: string;

  public static $inject = [
    '$scope',
    '$uibModalInstance',
    'application',
    'pipelineTemplateConfig',
    'isNew',
    'pipelineId',
    'executionId',
  ];
  constructor(
    private $scope: IScope,
    private $uibModalInstance: IModalInstanceService,
    private application: Application,
    public pipelineTemplateConfig: IPipelineTemplateConfig,
    public isNew: boolean,
    private pipelineId: string,
    private executionId: string,
  ) {}

  public $onInit(): void {
    this.initialize();
  }

  public initialize(): void {
    const { config } = this.pipelineTemplateConfig;
    const defaultConfiguration = { inherit: ['expectedArtifacts', 'parameters', 'triggers'] };
    const inherit: string[] = (config.configuration || defaultConfiguration).inherit;

    this.state.inheritTemplateExpectedArtifacts = inherit.includes('expectedArtifacts');
    this.state.inheritTemplateParameters = inherit.includes('parameters');
    this.state.inheritTemplateTriggers = inherit.includes('triggers');
    this.pipelineName = config.pipeline.name;
    this.source = config.pipeline.template.source;
    this.loadTemplate()
      .then(() => {
        this.groupVariableMetadata();
        this.initializeVariables();
        if (!this.variables.length) {
          this.state.noVariables = true;
        }
      })
      .then(() => (this.state.loading = false))
      .catch(() => {
        Object.assign(this.state, { loading: false, error: false, planErrors: null, loadingError: true });
      });
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

  public formIsValid(): boolean {
    return this.variables.every((v) => v.errors.length === 0);
  }

  public submit(): PromiseLike<void> {
    const config = this.buildConfig();
    return PipelineTemplateReader.getPipelinePlan(config)
      .then((plan) => {
        const { parameterConfig, expectedArtifacts, triggers } = plan;
        const inherited = {
          ...config,
          ...(this.state.inheritTemplateParameters && parameterConfig ? { parameterConfig } : {}),
          ...(this.state.inheritTemplateExpectedArtifacts && expectedArtifacts ? { expectedArtifacts } : {}),
          ...(this.state.inheritTemplateTriggers && triggers ? { triggers } : {}),
        };
        this.$uibModalInstance.close({ plan, config: inherited });
      })
      .catch((response: IHttpPromiseCallbackArg<IPipelineTemplatePlanResponse>) => {
        Object.assign(this.state, { loading: false, error: true, planErrors: response.data && response.data.errors });
      });
  }

  public dismissError(): void {
    Object.assign(this.state, { error: false, planErrors: null, loadingError: false });
  }

  public buildConfig(): IPipelineTemplateConfig {
    const inheritConfig = [
      ...(this.state.inheritTemplateParameters ? ['parameters'] : []),
      ...(this.state.inheritTemplateExpectedArtifacts ? ['expectedArtifacts'] : []),
      ...(this.state.inheritTemplateTriggers ? ['triggers'] : []),
    ];

    return {
      ...(this.pipelineTemplateConfig || {}),
      type: 'templatedPipeline',
      name: this.pipelineName,
      application: this.application.name,
      config: {
        schema: '1',
        pipeline: {
          name: this.pipelineName,
          application: this.application.name,
          pipelineConfigId: this.pipelineId,
          template: { source: this.source },
          variables: this.transformVariablesForPipelinePlan(),
        },
        configuration: {
          inherit: inheritConfig,
        },
      },
    };
  }

  private loadTemplate(): PromiseLike<void> {
    return PipelineTemplateReader.getPipelineTemplateFromSourceUrl(this.source, this.executionId, this.pipelineId).then(
      (template) => {
        this.template = template;
      },
    );
  }

  private transformVariablesForPipelinePlan(): { [key: string]: any } {
    return chain(this.variables || [])
      .cloneDeep()
      .map((v) => {
        if (v.type === 'object') {
          v.value = load(v.value);
        } else if (v.type === 'int') {
          return [v.name, parseInt(v.value, 10)];
        } else if (v.type === 'float') {
          return [v.name, parseFloat(v.value)];
        }
        return [v.name, v.value];
      })
      .fromPairs()
      .value();
  }

  public handleVariableChange = (newVariable: IVariable): void => {
    const oldVariable = this.getVariable(newVariable.name);
    newVariable.errors = VariableValidatorService.validate(newVariable);
    this.variables = without(this.variables, oldVariable).concat([newVariable]);

    // `handleVariableChange` is passed to a React component, and Angular has no idea when it has been called.
    this.$scope.$digest();
  };

  private getVariable(name: string): IVariable {
    return this.variables.find((v) => v.name === name);
  }

  private groupVariableMetadata(): void {
    this.variableMetadataGroups = [];
    (this.template.variables || []).forEach((v) => {
      if (v.group) {
        this.addToGroup(v.group, v);
      } else {
        this.addToGroup('Ungrouped', v);
      }
    });
  }

  private addToGroup(groupName: string, metadata: IVariableMetadata): void {
    const group = this.variableMetadataGroups.find((g) => g.name === groupName);
    if (group) {
      group.variableMetadata.push(metadata);
    } else {
      this.variableMetadataGroups.push({ name: groupName, variableMetadata: [metadata] });
    }
  }

  private initializeVariables(): void {
    this.variables = (this.template.variables || []).map((v) => {
      return {
        name: v.name,
        type: v.type || 'string',
        errors: [],
        value: this.getInitialVariableValue(v),
        hideErrors: true,
      };
    });
    this.variables.forEach((v) => (v.errors = VariableValidatorService.validate(v)));
  }

  private getInitialVariableValue(variable: IVariableMetadata): any {
    if (has(this.pipelineTemplateConfig, `config.pipeline.variables.${variable.name}`)) {
      let value = this.pipelineTemplateConfig.config.pipeline.variables[variable.name];
      if (variable.type === 'object' && value) {
        value = dump(value);
      }
      return value;
    } else if (variable.type === 'object' && has(variable, 'defaultValue')) {
      return dump(variable.defaultValue);
    } else {
      return variable.type === 'list' && !variable.defaultValue ? [''] : variable.defaultValue;
    }
  }
}

export const CONFIGURE_PIPELINE_TEMPLATE_MODAL_CTRL = 'spinnaker.core.pipeline.configureTemplate.modal.controller';
module(CONFIGURE_PIPELINE_TEMPLATE_MODAL_CTRL, []).controller(
  'ConfigurePipelineTemplateModalCtrl',
  ConfigurePipelineTemplateModalController,
);
