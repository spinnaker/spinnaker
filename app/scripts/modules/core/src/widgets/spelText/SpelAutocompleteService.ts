import { IQService } from 'angular';

import { JsonListBuilder } from './JsonListBuilder';
import { IExecution, IPipeline, IStage } from '../../domain';
import { ExecutionService } from '../../pipeline';

interface IBracket {
  open: string;
  close: string;
}

interface ITextcompleteConfigElement {
  id: string;

  [k: string]: any;
}

interface IHelperParam {
  name: string;
  type: string;
}

interface IExecutionCache {
  [k: string]: IExecution;
}

export class SpelAutocompleteService {
  private executionCache: IExecutionCache = {};

  private brackets: IBracket[] = [
    { open: '(', close: ')' },
    { open: '[', close: ']' },
  ];

  private quotes: IBracket[] = [
    { open: "'", close: "'" },
    { open: '"', close: '"' },
  ];

  private helperFunctions = [
    'alphanumerical',
    'readJson',
    'fromUrl',
    'propertiesFromUrl',
    'jsonFromUrl',
    'judgment',
    'stage',
    'toBoolean',
    'toFloat',
    'toInt',
    'toJson',
    'toBase64',
    'fromBase64',
    'cfServiceKey',
  ];

  private helperParams = [
    'execution',
    'parameters',
    'trigger',
    'scmInfo',
    'scmInfo.sha1',
    'scmInfo.branch',
    'deployedServerGroups',
  ];

  private codedHelperParams: IHelperParam[] = this.helperParams.map((param: string) => {
    return { name: param, type: 'param' };
  });

  public textcompleteConfig: ITextcompleteConfigElement[] = [
    {
      id: 'SpEL wrapper',
      match: /\$(\w*)$/,
      search: function (_: any, callback: (value: string[]) => void) {
        callback(['${...}']);
      },
      index: 1,
      replace: function replace() {
        return ['${ ', ' }'];
      },
    },
    {
      id: 'match quotes',
      match: /(["'])(\w*)$/,
      index: 1,
      search: (term: string, callback: (value: IBracket[]) => void) => {
        callback(this.quotes.filter((bracket: IBracket) => bracket.open.indexOf(term) === 0));
      },
      template: function () {
        return `'...'`;
      },
      replace: function replace() {
        return [`'`, `'`];
      },
    },
    {
      id: 'match brackets',
      match: /([[('])(\w*)$/,
      index: 1,
      search: (term: string, callback: (value: IBracket[]) => void) => {
        callback(this.brackets.filter((bracket: IBracket) => bracket.open.indexOf(term) === 0));
      },
      template: (value: IBracket) => {
        return `${value.open}...${value.close}`;
      },
      replace: (bracket: IBracket) => {
        return [`${bracket.open}  `, `  ${bracket.close}`];
      },
    },
  ];

  constructor(private $q: IQService, private executionService: ExecutionService) {
    'ngInject';
  }

  private paramInList(checkParam: { name: string }) {
    return (testParam: { name: string }) => checkParam.name === testParam.name;
  }

  private addToTextcompleteConfig(
    configList: ITextcompleteConfigElement[],
    textcompleteConfig: ITextcompleteConfigElement[],
  ): ITextcompleteConfigElement[] {
    const textcompleteConfigCopy = textcompleteConfig.slice(0);

    for (const newConfig of configList) {
      if (textcompleteConfig.filter((config) => config.id === newConfig.id).length === 0) {
        textcompleteConfigCopy.push(newConfig);
      }
    }

    return textcompleteConfigCopy;
  }

  private listSearchFn(list: any) {
    return (term: any, callback: any) => {
      callback(
        list.filter((item: any) => {
          if (item.leaf.includes(term)) {
            return item;
          }
        }),
      );
    };
  }

  private leafTemplateFn(stage: any) {
    return `${
      stage.leaf
    } <span class="glyphicon glyphicon-triangle-right spel-value-separator"/> <span class="marker value"
      title="${stage.value}">${stage.value.length > 90 ? `${stage.value.slice(0, 90)}...` : stage.value}</span>`;
  }

  private addExecutionForAutocomplete(execution: IExecution, textcompleteConfig: ITextcompleteConfigElement[]) {
    if (execution) {
      const executionList = JsonListBuilder.convertJsonKeysToBracketedList(execution, ['task']);
      const configList = [
        {
          id: `execution: ${execution.id}`,
          match: /execution(\w*|\s*)$/,
          index: 1,
          search: this.listSearchFn(executionList),
          template: this.leafTemplateFn,
          replace: (value: any) => {
            return `execution${value.leaf}`;
          },
        },
      ];
      return this.addToTextcompleteConfig(configList, textcompleteConfig);
    }
    return textcompleteConfig;
  }

  private addDeployedServerGroupsForAutoComplete(
    execution: IExecution,
    textcompleteConfig: ITextcompleteConfigElement[],
  ): ITextcompleteConfigElement[] {
    if (execution && execution.context && execution.context.deploymentDetails) {
      const deployList = JsonListBuilder.convertJsonKeysToBracketedList(execution.context.deploymentDetails);
      const configList = [
        {
          id: `deploymentServerGroups: ${execution.id}`,
          match: /deployedServerGroups(\w*|\s*)$/,
          index: 1,
          search: this.listSearchFn(deployList),
          template: this.leafTemplateFn,
          replace: (value: any) => {
            return `deployedServerGroups${value.leaf}`;
          },
        },
      ];
      return this.addToTextcompleteConfig(configList, textcompleteConfig);
    }
    return textcompleteConfig;
  }

  private addStageDataForAutocomplete(
    pipeline: IPipeline | IExecution,
    textcompleteConfig: ITextcompleteConfigElement[],
  ): ITextcompleteConfigElement[] {
    if (pipeline && pipeline.stages) {
      const configList = (pipeline.stages as IStage[]).map((stage) => {
        const stageList = JsonListBuilder.convertJsonKeysToBracketedList(stage, ['task']);

        return {
          id: `stage config for ${stage.name}`,
          match: new RegExp(`#stage\\(\\s*'${JsonListBuilder.escapeForRegEx(stage.name)}'\\s*\\)(.*)$`),
          index: 1,
          search: this.listSearchFn(stageList),
          template: this.leafTemplateFn,
          replace: (param: any) => {
            return `#stage('${stage.name}')${param.leaf}`;
          },
        };
      });
      return this.addToTextcompleteConfig(configList, textcompleteConfig);
    }

    return textcompleteConfig;
  }

  private addManualJudgementConfigForAutocomplete(
    pipeline: IPipeline | IExecution,
    textcompleteConfig: ITextcompleteConfigElement[],
  ): ITextcompleteConfigElement[] {
    if (pipeline && pipeline.stages) {
      const manualJudgementStageList = pipeline.stages.filter((stage) => stage.type === 'manualJudgment');

      const configList = manualJudgementStageList.map((stage) => {
        const stageList = JsonListBuilder.convertJsonKeysToBracketedList(stage);

        return {
          id: `judgement config for ${stage.name}`,
          match: new RegExp(`#judgment\\(\\s*'\\s*${JsonListBuilder.escapeForRegEx(stage.name)}'\\s*\\)(.*)$`),
          index: 1,
          search: this.listSearchFn(stageList),
          template: this.leafTemplateFn,
          replace: (param: any) => {
            return `#judgement('${stage.name}')${param.leaf}`;
          },
        };
      });

      return this.addToTextcompleteConfig(configList, textcompleteConfig);
    }

    return textcompleteConfig;
  }

  private addTriggerConfigForAutocomplete(
    pipeline: IExecution,
    textcompleteConfig: ITextcompleteConfigElement[],
  ): ITextcompleteConfigElement[] {
    if (pipeline && pipeline.trigger) {
      const triggerAsList = [pipeline.trigger];
      const configList = triggerAsList.map((trigger) => {
        const triggerInfoList = JsonListBuilder.convertJsonKeysToBracketedList(trigger);
        return {
          id: `trigger config: ${trigger.type}`,
          match: /trigger(\w*|\s*)$/,
          index: 1,
          search: this.listSearchFn(triggerInfoList),
          template: this.leafTemplateFn,
          replace: (value: any) => {
            return `trigger${value.leaf}`;
          },
        };
      });

      return this.addToTextcompleteConfig(configList, textcompleteConfig);
    }

    return textcompleteConfig;
  }

  private addParameterConfigForAutocomplete(
    pipeline: IPipeline,
    textcompleteConfig: ITextcompleteConfigElement[],
  ): ITextcompleteConfigElement[] {
    if (pipeline && pipeline.parameterConfig) {
      const paramsAsList = [pipeline.parameterConfig];
      const configList = paramsAsList.map((params) => {
        const paramsInfoList = JsonListBuilder.convertJsonKeysToBracketedList(params);
        return {
          id: `parameter config: ${Object.keys(params).join(',')}`,
          match: /parameters(\w*|\s*)$/,
          index: 1,
          search: this.listSearchFn(paramsInfoList),
          template: this.leafTemplateFn,
          replace: (value: any) => {
            return `parameters${value.leaf}`;
          },
        };
      });

      return this.addToTextcompleteConfig(configList, textcompleteConfig);
    }

    return textcompleteConfig;
  }

  private hasBuildTriggerOrStage(pipeline: IExecution & IPipeline, ...systems: string[]): boolean {
    return systems.some(
      (system) =>
        (pipeline.triggers && pipeline.triggers.some((trigger) => trigger.type === system)) ||
        pipeline.stages.some((stage) => stage.type === system),
    );
  }

  private addStageNamesToCodeHelperList(
    pipeline: IExecution & IPipeline,
    textcompleteConfig: ITextcompleteConfigElement[],
  ): ITextcompleteConfigElement[] {
    if (pipeline && pipeline.stages) {
      let codedHelperParamsCopy = this.codedHelperParams.slice(0);

      const pipelineHasParameters = pipeline.parameterConfig && pipeline.parameterConfig.length;
      codedHelperParamsCopy = pipelineHasParameters
        ? codedHelperParamsCopy
        : codedHelperParamsCopy.filter((param) => param.name !== 'parameters');

      codedHelperParamsCopy = this.hasBuildTriggerOrStage(pipeline, 'jenkins', 'concourse')
        ? codedHelperParamsCopy
        : codedHelperParamsCopy.filter((param) => !param.name.includes('scmInfo'));

      pipeline.stages.forEach((stage) => {
        const newParam = { name: stage.name, type: stage.type };
        if (codedHelperParamsCopy.filter(this.paramInList(newParam)).length === 0) {
          codedHelperParamsCopy.push({ name: stage.name, type: 'stage' });
        }
      });

      const configList = [
        {
          id: 'params',
          match: /(\s*|\w*)\?(\s*|\w*|')$/,
          index: 2,
          search: (term: string, callback: (value: IHelperParam[]) => void) => {
            callback(
              codedHelperParamsCopy.filter((param: any) => param.name.includes(term) || param.type.includes(term)),
            );
          },
          template: (value: IHelperParam) => `<span class="marker ${value.type}"/> ${value.name}`,
          replace: (param: IHelperParam) => `${param.name}`,
        },
      ];

      return this.addToTextcompleteConfig(configList, textcompleteConfig);
    }
    return textcompleteConfig;
  }

  private addHelperFunctionsBasedOnStages(
    pipeline: IPipeline | IExecution,
    textcompleteConfig: ITextcompleteConfigElement[],
  ): ITextcompleteConfigElement[] {
    if (pipeline && pipeline.stages) {
      let helperFunctionsCopy = this.helperFunctions.slice(0);
      const hasManualJudmentStage = pipeline.stages.some((stage) => stage.type === 'manualJudgment');
      if (!hasManualJudmentStage) {
        helperFunctionsCopy = this.helperFunctions.filter((fnName) => fnName !== 'judgment');
      }
      const hasCreateServiceKeyStage = pipeline.stages.some((stage) => stage.type === 'createServiceKey');
      if (!hasCreateServiceKeyStage) {
        helperFunctionsCopy = this.helperFunctions.filter((fnName) => fnName !== 'cfServiceKey');
      }

      const configList = [
        {
          id: 'helper functions',
          match: /#([\w.]*)$/,
          index: 1,
          search: (term: string, callback: (value: string[]) => void) => {
            callback(helperFunctionsCopy.filter((helper: string) => helper.startsWith(term)));
          },
          template: (value: string) => `<span class="marker function"/> #${value}`,
          replace: (helper: string) => (helper === 'toJson' ? [`#${helper}(`, ')'] : [`#${helper}( '`, `' )`]),
        },
      ];

      return this.addToTextcompleteConfig(configList, textcompleteConfig);
    }
    return textcompleteConfig;
  }

  private getLastExecutionByPipelineConfig(pipelineConfig: IPipeline): PromiseLike<IExecution> {
    if (this.executionCache[pipelineConfig.id]) {
      return this.$q.when(this.executionCache[pipelineConfig.id]);
    } else {
      return this.executionService
        .getLastExecutionForApplicationByConfigId(pipelineConfig.application, pipelineConfig.id)
        .then((execution) => {
          if (execution) {
            this.executionCache[pipelineConfig.id] = execution;
            return execution;
          } else {
            return null;
          }
        });
    }
  }

  // visible for testing
  public buildTextCompleteConfig(pipeline: IPipeline & IExecution): ITextcompleteConfigElement[] {
    return this.addStageNamesToCodeHelperList(
      pipeline,
      this.addStageDataForAutocomplete(
        pipeline,
        this.addManualJudgementConfigForAutocomplete(
          pipeline,
          this.addTriggerConfigForAutocomplete(
            pipeline,
            this.addParameterConfigForAutocomplete(
              // TODO does this not work on stages?
              pipeline,
              this.addHelperFunctionsBasedOnStages(
                pipeline,
                this.addExecutionForAutocomplete(
                  pipeline,
                  this.addDeployedServerGroupsForAutoComplete(pipeline, this.textcompleteConfig.slice(0)),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  public addPipelineInfo(pipelineConfig: IPipeline) {
    if (pipelineConfig && pipelineConfig.id) {
      return this.getLastExecutionByPipelineConfig(pipelineConfig)
        .then((lastExecution) => lastExecution || pipelineConfig)
        .then((pipeline: IPipeline & IExecution) => this.buildTextCompleteConfig(pipeline));
    } else {
      return this.$q.when(this.textcompleteConfig);
    }
  }
}
