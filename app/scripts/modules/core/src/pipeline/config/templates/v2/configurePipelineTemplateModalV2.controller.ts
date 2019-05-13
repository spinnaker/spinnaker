import { module, IController, IScope, IHttpPromiseCallbackArg, IPromise } from 'angular';
import { IModalInstanceService } from 'angular-ui-bootstrap';
import { without, chain, has } from 'lodash';

import { Application } from 'core/application/application.model';
import { IPipelineTemplateConfigV2 } from 'core/domain';
import { PipelineTemplateV2Service } from 'core/pipeline';
import { VariableValidatorService } from '../validators/variableValidator.service';

import {
  PipelineTemplateReader,
  IVariableMetadata,
  IPipelineTemplatePlanResponse,
  IPipelineTemplate,
  IPipelineTemplatePlanError,
} from 'core/pipeline/config/templates/PipelineTemplateReader';
import { IVariable } from '../inputs/variableInput.service';

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
  inheritTemplateNotifications: boolean;
  inheritTemplateExpectedArtifacts: boolean;
}
// Logic lifted from ConfigurePipelineTemplateModalController and slightly refactored to support MPTV2.
// TODO: Once MPTV1 is fully migrated, delete ConfigurePipelineTemplateModalController.
export class ConfigurePipelineTemplateModalV2Controller implements IController {
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
    inheritTemplateNotifications: true,
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
    public pipelineTemplateConfig: IPipelineTemplateConfigV2,
    public isNew: boolean,
    private pipelineId: string,
    private executionId: string,
  ) {}

  public $onInit(): void {
    this.initialize();
  }

  public initialize(): void {
    const { exclude, name, template } = this.pipelineTemplateConfig;

    const excluded = exclude || [];
    this.state.inheritTemplateNotifications = !excluded.includes('notifications');
    this.state.inheritTemplateParameters = !excluded.includes('parameters');
    this.state.inheritTemplateTriggers = !excluded.includes('triggers');
    this.state.inheritTemplateExpectedArtifacts = !excluded.includes('expectedArtifacts');

    this.pipelineName = name;
    this.source = template.reference;

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
    return this.variables.every(v => v.errors.length === 0);
  }

  public submit(): IPromise<void> {
    const config = this.buildConfig();

    return PipelineTemplateReader.getPipelinePlan(config)
      .then(plan => {
        const { parameterConfig, notifications, triggers, expectedArtifacts } = plan;
        const {
          inheritTemplateParameters,
          inheritTemplateNotifications,
          inheritTemplateTriggers,
          inheritTemplateExpectedArtifacts,
        } = this.state;

        const configWithInheritedValues = {
          ...config,
          ...(inheritTemplateParameters && parameterConfig ? { parameterConfig } : {}),
          ...(inheritTemplateNotifications && notifications ? { notifications } : {}),
          ...(inheritTemplateTriggers && triggers ? { triggers } : {}),
          ...(inheritTemplateExpectedArtifacts && expectedArtifacts ? { expectedArtifacts } : {}),
        };

        this.$uibModalInstance.close({ plan, config: configWithInheritedValues });
      })
      .catch((response: IHttpPromiseCallbackArg<IPipelineTemplatePlanResponse>) => {
        Object.assign(this.state, { loading: false, error: true, planErrors: response.data && response.data.errors });
      });
  }

  public dismissError(): void {
    Object.assign(this.state, { error: false, planErrors: null, loadingError: false });
  }

  public buildConfig(): IPipelineTemplateConfigV2 {
    const {
      application: { name: appName },
      state: {
        inheritTemplateNotifications,
        inheritTemplateParameters,
        inheritTemplateTriggers,
        inheritTemplateExpectedArtifacts,
      },
      pipelineName,
      pipelineTemplateConfig,
      source,
    } = this;

    const excludeConfig = [
      ...(inheritTemplateParameters ? [] : ['parameters']),
      ...(inheritTemplateNotifications ? [] : ['notifications']),
      ...(inheritTemplateTriggers ? [] : ['triggers']),
      ...(inheritTemplateExpectedArtifacts ? [] : ['expectedArtifacts']),
    ];

    return {
      ...(pipelineTemplateConfig || {}),
      type: 'templatedPipeline',
      name: pipelineName,
      application: appName,
      variables: this.transformVariablesForPipelinePlan(),
      exclude: excludeConfig,
      ...PipelineTemplateV2Service.getPipelineTemplateConfigV2(source),
    };
  }

  private loadTemplate(): IPromise<void> {
    return PipelineTemplateReader.getPipelineTemplateFromSourceUrl(this.source, this.executionId, this.pipelineId).then(
      template => {
        this.template = template;
      },
    );
  }

  private transformVariablesForPipelinePlan(): { [key: string]: any } {
    return chain(this.variables || [])
      .cloneDeep()
      .map(v => {
        if (v.type === 'object') {
          v.value = JSON.parse(v.value);
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
      this.variableMetadataGroups.push({ name: groupName, variableMetadata: [metadata] });
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
    this.variables.forEach(v => (v.errors = VariableValidatorService.validate(v)));
  }

  private getInitialVariableValue(variable: IVariableMetadata): any {
    if (has(this.pipelineTemplateConfig, `variables.${variable.name}`)) {
      let value = this.pipelineTemplateConfig.variables[variable.name];
      if (variable.type === 'object' && value) {
        value = JSON.stringify(value);
      }
      return value;
    } else if (variable.type === 'object' && has(variable, 'defaultValue')) {
      return JSON.stringify(variable.defaultValue);
    } else {
      return variable.type === 'list' && !variable.defaultValue ? [''] : variable.defaultValue;
    }
  }
}

export const CONFIGURE_PIPELINE_TEMPLATE_MODAL_V2_CTRL =
  'spinnaker.core.pipeline.configureTemplate.modal.v2.controller';
module(CONFIGURE_PIPELINE_TEMPLATE_MODAL_V2_CTRL, []).controller(
  'ConfigurePipelineTemplateModalV2Ctrl',
  ConfigurePipelineTemplateModalV2Controller,
);
