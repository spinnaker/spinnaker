'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.widget.spelAutocomplete', [
    require('./jsonListBuilder'),
    require('../../delivery/service/execution.service'),
  ])
  .factory('spelAutocomplete', function($q, jsonListBuilder, executionService) {
    let brackets = [{open: '(', close: ')'}, {open: '[', close: ']'}, ];
    let quotes = [{open: '\'', close: '\'' }, {open: '\"', close: '\"' }];
    let helperFunctions = ['alphanumerical', 'readJson', 'fromUrl', 'jsonFromUrl', 'judgment', 'stage', 'toBoolean', 'toFloat', 'toInt', 'toJson'];
    let helperParams = ['execution', 'parameters', 'trigger', 'scmInfo', 'scmInfo.sha1', 'scmInfo.branch', 'deployedServerGroups'];
    let codedHelperParams = helperParams.map((param) => {
      return {name: param, type: 'param'};
    });

    let textcompleteConfig = [
      {
        id: 'SpEL wrapper',
        match: /\$(\w*)$/,
        search: function(item, callback) {
          callback(['${...}']);
        },
        index: 1,
        replace: function replace() {
          return ['${ ', ' }'];
        }
      },
      {
        id: 'match quotes',
        match: /("|')(\w*)$/,
        index: 1,
        search: function(term, callback) {
          let found = quotes.filter((quote) => {
            if (quote.open.indexOf(term) === 0) {
              return quote;
            }
          });
          callback(found);
        },
        template: function() {
          return `'...'`;
        },
        replace: function replace() {
          return [`'` , `'`];
        }
      },
      {
        id: 'match brackets',
        match: /(\[|\(|')(\w*)$/,
        index: 1,
        search: function(term, callback) {
          let found = brackets.filter((bracket) => {
            if (bracket.open.indexOf(term) === 0) {
              return bracket;
            }
          });
          callback(found);
        },
        template: function(value) {
          return `${value.open}...${value.close}`;
        },
        replace: function replace(bracket) {
          return [`${bracket.open}  ` , `  ${bracket.close}`];
        }
      },
      {
        id: 'helper functions',
        match: /#(\w*)$/,
        index: 1,
        search: function (term, callback) {
          callback(helperFunctions.filter((helper) => {
            if(helper.indexOf(term) === 0) {
              return helper;
            }
          }));
        },
        template: (value) => {
          return `<span class="marker function"></span> #${value}`;
        },

        replace: function replace(helper) {
          if (helper === 'toJson') {
            return [`#${helper}(`, ')'];
          }
          return [`#${helper}( '`, `' )`];
        }
      }
    ];


    let paramInList = (checkParam) => {
      return (testParam) => checkParam.name === testParam.name;
    };


    let addToTextcompleteConfig = (configList = [], textcompleteConfig) => {
      let textcompleteConfigCopy = textcompleteConfig.slice(0);

      configList.forEach((newConfig) => {
        if(textcompleteConfig.filter( (config) => config.id === newConfig.id ).length === 0) {
          return textcompleteConfigCopy.push(newConfig);
        }
      });

      return textcompleteConfigCopy;
    };


    let addStageDataForAutocomplete = (pipeline, textcompleteConfig) => {
      if(pipeline && pipeline.stages) {
        let configList = pipeline.stages.map((stage) => {

          let stageList = jsonListBuilder.convertJsonKeysToBracketedList(stage, ['task']);

          return {
            id: `stage config for ${stage.name}`,
            match: new RegExp(`#stage\\(\\s*'${jsonListBuilder.escapeForRegEx(stage.name)}'\\s*\\)(.*)$`),
            index: 1,
            search: (term, callback) => {
              callback(stageList.filter( (item) => {
                if (item.indexOf(term) > -1) {
                  return item;
                }
              }));
            },
            replace: (param) => {
              return `#stage('${stage.name}')${param}`;
            }
          };

        });
        return addToTextcompleteConfig(configList, textcompleteConfig);
      }

      return textcompleteConfig;

    };


    let addManualJudgementConfigForAutocomplete = (pipeline, textcompleteConfig) => {
      if(pipeline && pipeline.stages) {

        let manualJudgementStageList = pipeline.stages.filter((stage) => stage.type === 'manualJudgment');

        let configList = manualJudgementStageList.map((stage) => {
          let stageList = jsonListBuilder.convertJsonKeysToBracketedList(stage);

          return {
            id: `judgement config for ${stage.name}`,
            match: new RegExp(`#judgement\\(\\s*'\\s*${jsonListBuilder.escapeForRegEx(stage.name)}'\\s*\\)(.*)$`),
            index: 1,
            search: (term, callback) => {
              callback(stageList.filter((item) => {
                if (item.indexOf(term) > -1) {
                  return item;
                }
              }));
            },
            replace: (param) => {
              return `#judgement('${stage.name}')${param}`;
            }
          };

        });

        return addToTextcompleteConfig(configList, textcompleteConfig);
      }

      return textcompleteConfig;

    };


    let addTriggerConfigForAutocomplete = (pipeline, textcompleteConfig) => {

      if(pipeline && pipeline.trigger) {
        let triggerAsList = [pipeline.trigger];
        let configList = triggerAsList.map((trigger) => {
          let triggerInfoList = jsonListBuilder.convertJsonKeysToBracketedList(trigger);
          return {
            id: `trigger config: ${trigger.type}`,
            match: /trigger(\w*|\s*)$/,
            index: 1,
            search: (term, callback) => {
              callback(triggerInfoList.filter( (item) => {
                if (item.indexOf(term) > -1) {
                  return item;
                }
              }));
            },
            replace: function replace(value) {
              return `trigger${value}`;
            }
          };
        });

        return addToTextcompleteConfig(configList, textcompleteConfig);
      }

      return textcompleteConfig;
    };


    let addParameterConfigForAutocomplete = (pipeline, textcompleteConfig) => {
      if(pipeline && pipeline.trigger && pipeline.trigger.parameters) {
        let paramsAsList = [pipeline.trigger.parameters];
        let configList = paramsAsList.map((params) => {
          let paramsInfoList = jsonListBuilder.convertJsonKeysToBracketedList(params);
          return {
            id: `parameter config: ${Object.keys(params).join(',')}`,
            match: /parameters(\w*|\s*)$/,
            index: 1,
            search: (term, callback) => {
              callback(paramsInfoList.filter( (item) => {
                if (item.indexOf(term) > -1) {
                  return item;
                }
              }));
            },
            replace: function replace(value) {
              return `parameters${value}`;
            }
          };
        });

        return addToTextcompleteConfig(configList, textcompleteConfig);
      }

      return textcompleteConfig;
    };


    let addStageNamesToCodeHelperList = (pipeline, textcompleteConfig) => {
      if (pipeline && pipeline.stages) {
        let codedHelperParamsCopy = codedHelperParams.slice(0);

        pipeline.stages.forEach((stage) => {
          let newParam = {name: stage.name, type: stage.type};
          if (codedHelperParamsCopy.filter(paramInList(newParam)).length === 0) {
            codedHelperParamsCopy.push({name: stage.name, type: 'stage'});
          }
        });

        let configList = [{
          id: 'params',
          match: /(\s*|\w*)\?(\s*|\w*|')$/,
          index: 2,
          search: function (term, callback) {
            callback(codedHelperParamsCopy.filter((param) => {
              if (param.name.indexOf(term) > -1 || param.type.indexOf(term) > -1) {
                return param;
              }
            }));
          },
          template: function (value) {
            return `<span class="marker ${value.type}"></span> ${value.name}`;
          },
          replace: function replace (param) {
            return `${param.name}`;
          }
        }];

        return addToTextcompleteConfig(configList, textcompleteConfig);
      }
      return textcompleteConfig;
    };

    let executionCache = {};

    let getLastExecutionByPipelineConfig = (pipelineConfig) => {
      if(executionCache[pipelineConfig.id]) {
        return $q.when(executionCache[pipelineConfig.id]);
      } else {
        return executionService
          .getLastExecutionForApplicationByConfigId(pipelineConfig.application, pipelineConfig.id)
          .then((execution) => {
            if(execution) {
              executionCache[pipelineConfig.id] = execution;
              return execution;
            } else {
              return null;
            }
          });
      }
    };

    let addPipelineInfo = (pipelineConfig) => {
      if(pipelineConfig) {
        return getLastExecutionByPipelineConfig(pipelineConfig)
          .then((lastExecution) => {
            return lastExecution || pipelineConfig;
          })
          .then((pipeline) => {
            return addStageNamesToCodeHelperList(
              pipeline,
              addStageDataForAutocomplete(
                pipeline,
                addManualJudgementConfigForAutocomplete(
                  pipeline,
                  addTriggerConfigForAutocomplete(
                    pipeline,
                    addParameterConfigForAutocomplete(
                      pipeline,
                      textcompleteConfig.slice(0)
                    )
                  )
                )
              )
            );
          });
      } else {
        return $q.when(textcompleteConfig);
      }

    };

    return {
      textcompleteConfig: textcompleteConfig,
      addPipelineInfo: addPipelineInfo
    };

  });
