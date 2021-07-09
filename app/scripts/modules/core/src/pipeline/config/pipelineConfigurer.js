'use strict';

import * as angular from 'angular';
import _ from 'lodash';

import { DeletePipelineModal } from './actions/delete/DeletePipelineModal';
import { DisablePipelineModal } from './actions/disable/DisablePipelineModal';
import { EnablePipelineModal } from './actions/enable/EnablePipelineModal';
import { ShowPipelineHistoryModal } from './actions/history/ShowPipelineHistoryModal';
import { LockPipelineModal } from './actions/lock/LockPipelineModal';
import { PIPELINE_CONFIG_ACTIONS } from './actions/pipelineConfigActions.module';
import { EditPipelineJsonModal } from './actions/pipelineJson/EditPipelineJsonModal';
import { RenamePipelineModal } from './actions/rename/RenamePipelineModal';
import { ShowPipelineTemplateJsonModal } from './actions/templateJson/ShowPipelineTemplateJsonModal';
import { UnlockPipelineModal } from './actions/unlock/UnlockPipelineModal';
import { ViewStateCache } from '../../cache';
import { CopyStageModal } from './copyStage/CopyStageModal';
import { EXECUTION_BUILD_TITLE } from '../executionBuild/ExecutionBuildTitle';
import { OVERRIDE_REGISTRY } from '../../overrideRegistry/override.registry';
import { ReactModal } from '../../presentation';
import { ExecutionsTransformer } from '../service/ExecutionsTransformer';
import { PipelineConfigService } from './services/PipelineConfigService';
import { PipelineTemplateWriter } from './templates/PipelineTemplateWriter';
import { PipelineTemplateV2Service } from './templates/v2/pipelineTemplateV2.service';
import { PipelineConfigValidator } from './validation/PipelineConfigValidator';

export const CORE_PIPELINE_CONFIG_PIPELINECONFIGURER = 'spinnaker.core.pipeline.config.pipelineConfigurer';
export const name = CORE_PIPELINE_CONFIG_PIPELINECONFIGURER; // for backwards compatibility
angular
  .module(CORE_PIPELINE_CONFIG_PIPELINECONFIGURER, [OVERRIDE_REGISTRY, PIPELINE_CONFIG_ACTIONS, EXECUTION_BUILD_TITLE])
  .directive('pipelineConfigurer', function () {
    return {
      restrict: 'E',
      scope: {
        pipeline: '=',
        application: '=',
        plan: '<',
        isTemplatedPipeline: '<',
        isV2TemplatedPipeline: '<',
        hasDynamicSource: '<',
        templateError: '<',
      },
      controller: 'PipelineConfigurerCtrl as pipelineConfigurerCtrl',
      templateUrl: require('./pipelineConfigurer.html'),
    };
  })
  .controller('PipelineConfigurerCtrl', [
    '$scope',
    '$uibModal',
    '$timeout',
    '$window',
    '$q',
    '$state',
    'executionService',
    'overrideRegistry',
    '$location',
    function ($scope, $uibModal, $timeout, $window, $q, $state, executionService, overrideRegistry, $location) {
      const ctrl = this;
      const markDirty = () => {
        if (!$scope.viewState.original) {
          setOriginal($scope.pipeline);
        }
        this.setViewState({ isDirty: $scope.viewState.original !== angular.toJson($scope.pipeline) });
      };
      // For standard pipelines, a 'renderablePipeline' is just the pipeline config.
      // For both v1 and v2 templated pipelines, a 'renderablePipeline' is the pipeline template plan, and '$scope.pipeline' is the template config.
      $scope.renderablePipeline = $scope.plan || $scope.pipeline;
      // Watch for non-reference changes to renderablePipline and make them reference changes to make React happy
      $scope.$watch('renderablePipeline', (newValue, oldValue) => newValue !== oldValue && this.updatePipeline(), true);

      this.warningsPopover = require('./warnings.popover.html');
      const configViewStateCache = ViewStateCache.get('pipelineConfig');

      function buildCacheKey() {
        return PipelineConfigService.buildViewStateCacheKey($scope.application.name, $scope.pipeline.id);
      }

      $scope.viewState = configViewStateCache.get(buildCacheKey()) || {
        section: 'triggers',
        stageIndex: 0,
        loading: false,
        revertCount: 0,
      };

      const setOriginal = (pipeline) => {
        $scope.viewState.original = angular.toJson(pipeline);
        $scope.viewState.originalRenderablePipeline = angular.toJson($scope.renderablePipeline);
        this.updatePipeline();
      };

      const getOriginal = () => angular.fromJson($scope.viewState.original);

      const getOriginalRenderablePipeline = () => angular.fromJson($scope.viewState.originalRenderablePipeline);

      // keep it separate from viewState, since viewState is cached...
      $scope.navMenuState = {
        showMenu: false,
      };

      this.hideNavigationMenu = () => {
        // give the navigate method a chance to fire before hiding the menu
        $timeout(() => {
          $scope.navMenuState.showMenu = false;
        }, 200);
      };

      this.deletePipeline = () => {
        ReactModal.show(DeletePipelineModal, { pipeline: $scope.pipeline, application: $scope.application });
      };

      this.addStage = (newStage = { isNew: true }) => {
        $scope.renderablePipeline.stages = $scope.renderablePipeline.stages || [];
        newStage.refId = Math.max(0, ...$scope.renderablePipeline.stages.map((s) => Number(s.refId) || 0)) + 1 + '';
        newStage.requisiteStageRefIds = [];
        if ($scope.renderablePipeline.stages.length && $scope.viewState.section === 'stage') {
          newStage.requisiteStageRefIds.push($scope.renderablePipeline.stages[$scope.viewState.stageIndex].refId);
        }
        $scope.renderablePipeline.stages.push(newStage);
        this.navigateToStage($scope.renderablePipeline.stages.length - 1);
      };

      this.copyExistingStage = () => {
        ReactModal.show(CopyStageModal, {
          application: $scope.application,
          forStrategyConfig: $scope.pipeline.strategy,
        })
          .then((stageTemplate) => ctrl.addStage(stageTemplate))
          .catch(() => {});
      };

      $scope.stageSortOptions = {
        axis: 'x',
        delay: 150,
        placeholder: 'btn btn-default drop-placeholder',
        'ui-floating': true,
        start: (e, ui) => {
          ui.placeholder.width(ui.helper.width()).height(ui.helper.height());
        },
        update: (e, ui) => {
          let itemScope = ui.item.scope();
          const currentPage = $scope.viewState.stageIndex;
          const startingPagePosition = itemScope.$index;
          const isCurrentPage = currentPage === startingPagePosition;

          $timeout(() => {
            itemScope = ui.item.scope(); // this is terrible but provides a hook for mocking in tests
            const newPagePosition = itemScope.$index;
            if (isCurrentPage) {
              ctrl.navigateToStage(newPagePosition);
            } else {
              const wasBefore = startingPagePosition < currentPage;
              const isBefore = newPagePosition <= currentPage;
              if (wasBefore !== isBefore) {
                const newCurrentPage = isBefore ? currentPage + 1 : currentPage - 1;
                ctrl.navigateToStage(newCurrentPage);
              }
            }
          });
        },
      };

      this.renamePipeline = () => {
        ReactModal.show(RenamePipelineModal, { pipeline: $scope.pipeline, application: $scope.application })
          .then((pipelineName) => {
            $scope.pipeline.name = pipelineName;
            return this.applyUpdateTs($scope.pipeline);
          })
          .catch(() => {});
      };

      this.editPipelineJson = () => {
        const modalProps = { dialogClassName: 'modal-lg modal-fullscreen' };
        ReactModal.show(EditPipelineJsonModal, { pipeline: $scope.pipeline, plan: $scope.plan }, modalProps)
          .then(() => {
            $scope.$broadcast('pipeline-json-edited');
            this.updatePipeline();
          })
          .catch(() => {});
      };

      // Enabling a pipeline simply toggles the disabled flag - it does not save any pending changes
      this.enablePipeline = () => {
        ReactModal.show(EnablePipelineModal, { pipeline: getOriginal() })
          .then(() => disableToggled(false))
          .catch(() => {});
      };

      this.exportPipelineTemplate = () => {
        const modalProps = { dialogClassName: 'modal-lg modal-fullscreen' };
        const pipeline = $scope.pipeline;
        const ownerEmail = _.get($scope, 'application.attributes.email', '');
        const template = PipelineTemplateV2Service.createPipelineTemplate(pipeline, ownerEmail);
        const templateProps = {
          template,
          saveTemplate: this.saveTemplate,
        };
        ReactModal.show(ShowPipelineTemplateJsonModal, templateProps, modalProps);
      };

      // Disabling a pipeline also just toggles the disabled flag - it does not save any pending changes
      this.disablePipeline = () => {
        ReactModal.show(DisablePipelineModal, { pipeline: getOriginal() })
          .then(() => disableToggled(true))
          .catch(() => {});
      };

      function disableToggled(isDisabled) {
        $scope.pipeline.disabled = isDisabled;
        const original = getOriginal();
        original.disabled = isDisabled;
        setOriginal(original);
      }

      // Locking a pipeline persists any pending changes
      this.lockPipeline = () => {
        ReactModal.show(LockPipelineModal, { pipeline: $scope.pipeline })
          .then((pipeline) => {
            $scope.pipeline.locked = pipeline.locked;
            setOriginal($scope.pipeline);
          })
          .catch(() => {});
      };

      this.unlockPipeline = () => {
        ReactModal.show(UnlockPipelineModal, { pipeline: $scope.pipeline })
          .then(() => {
            delete $scope.pipeline.locked;
            setOriginal($scope.pipeline);
          })
          .catch(() => {});
      };

      this.showHistory = () => {
        ReactModal.show(ShowPipelineHistoryModal, {
          pipelineConfigId: $scope.pipeline.id,
          isStrategy: $scope.pipeline.strategy,
          currentConfig: $scope.viewState.isDirty ? JSON.parse(angular.toJson($scope.pipeline)) : null,
        })
          .then((newConfig) => {
            $scope.renderablePipeline = newConfig;
            $scope.pipeline = newConfig;
            $scope.$broadcast('pipeline-json-edited');
            this.savePipeline();
          })
          .catch(() => {});
      };

      // Poor react setState
      this.setViewState = (newViewState) => {
        Object.assign($scope.viewState, newViewState);
        const viewState = _.clone($scope.viewState);
        $scope.$applyAsync(() => ($scope.viewState = viewState));
      };

      // Poor react setState
      this.updatePipeline = () => {
        $scope.$applyAsync(() => {
          $scope.renderablePipeline = _.clone($scope.renderablePipeline);
          // need to ensure references are maintained
          if ($scope.isTemplatedPipeline) {
            $scope.plan = $scope.renderablePipeline;
          } else {
            $scope.pipeline = $scope.renderablePipeline;
          }
        });
      };

      this.navigateToStage = (index, event) => {
        if (index < 0 || !$scope.renderablePipeline.stages || $scope.renderablePipeline.stages.length <= index) {
          this.setViewState({ section: 'triggers' });
          return;
        }
        this.setViewState({ section: 'stage', stageIndex: index });
        if (event && event.target && event.target.focus) {
          event.target.focus();
        }
      };

      this.navigateTo = (stage) => {
        if (stage.section === 'stage') {
          ctrl.navigateToStage(stage.index);
        } else {
          this.setViewState({ section: stage.section });
        }
      };

      // When using callbacks in a component that can be both angular and react, have to force binding in the angular world
      this.graphNodeClicked = this.navigateTo.bind(this);

      this.isActive = (section) => {
        return $scope.viewState.section === section;
      };

      this.stageIsActive = (index) => {
        return $scope.viewState.section === 'stage' && $scope.viewState.stageIndex === index;
      };

      this.removeStage = (stage) => {
        const stageIndex = $scope.renderablePipeline.stages.indexOf(stage);
        $scope.renderablePipeline.stages.splice(stageIndex, 1);
        $scope.renderablePipeline.stages.forEach((test) => {
          if (stage.refId && test.requisiteStageRefIds) {
            if (test.requisiteStageRefIds.includes(stage.refId)) {
              test.requisiteStageRefIds = test.requisiteStageRefIds.filter((id) => id !== stage.refId);
              if (!test.requisiteStageRefIds.length) {
                test.requisiteStageRefIds = [...stage.requisiteStageRefIds];
              }
            }
          }
        });
        if (stageIndex > 0) {
          this.setViewState({ stageIndex: $scope.viewState.stageIndex - 1 });
        }
        if (stageIndex === $scope.viewState.stageIndex && stageIndex === 0) {
          $scope.$broadcast('pipeline-json-edited');
        }
        if (!$scope.renderablePipeline.stages.length) {
          this.navigateTo({ section: 'triggers' });
        }
      };

      this.isValid = () => {
        return (
          _.every($scope.pipeline.stages, function (item) {
            return item['name'] && item['type'];
          }) && !ctrl.preventSave
        );
      };

      this.configureTemplate = () => {
        const controller = PipelineTemplateV2Service.isV2PipelineConfig($scope.pipeline)
          ? {
              name: 'ConfigurePipelineTemplateModalV2Ctrl',
              template: require('./templates/v2/configurePipelineTemplateModalV2.html'),
            }
          : {
              name: 'ConfigurePipelineTemplateModalCtrl',
              template: require('./templates/configurePipelineTemplateModal.html'),
            };

        this.setViewState({ loading: true });
        $uibModal
          .open({
            size: 'lg',
            templateUrl: controller.template,
            controller: `${controller.name} as ctrl`,
            resolve: {
              application: () => $scope.application,
              pipelineTemplateConfig: () => _.cloneDeep($scope.pipeline),
              isNew: () => $scope.pipeline.isNew,
              pipelineId: () => $scope.pipeline.id,
              executionId: () => $scope.renderablePipeline.executionId,
            },
          })
          .result.then(({ plan, config }) => {
            $scope.pipeline = config;
            delete $scope.pipeline.isNew;
            $scope.renderablePipeline = plan;
          })
          .catch(() => {})
          .finally(() => this.setViewState({ loading: false }));
      };

      this.applyUpdateTs = (toSave) => {
        return $scope.application.pipelineConfigs.refresh(true).then((pipelines) => {
          const latestFromServer = pipelines.find((p) => p.id === toSave.id);
          if (latestFromServer && latestFromServer.updateTs) {
            toSave.updateTs = latestFromServer.updateTs;
            this.updatePipelineConfig({ updateTs: latestFromServer.updateTs });
          }
          setOriginal(toSave);
          markDirty();
        });
      };

      this.savePipeline = () => {
        this.setViewState({ saving: true });
        const toSave = _.cloneDeep($scope.pipeline);
        PipelineConfigService.savePipeline(toSave)
          .then(() => this.applyUpdateTs(toSave))
          .then(
            () => {
              this.setViewState({
                saveError: false,
                saving: false,
              });
            },
            (err) =>
              this.setViewState({
                saveError: true,
                saving: false,
                saveErrorMessage: ctrl.getErrorMessage(err.data.message),
              }),
          );
      };

      this.getErrorMessage = (errorMsg) => {
        let msg = 'There was an error saving your pipeline';
        if (_.isString(errorMsg)) {
          msg += ': ' + errorMsg;
        }
        msg += '.';

        return msg;
      };

      this.getPipelineExecutions = () => {
        executionService
          .getExecutionsForConfigIds([$scope.pipeline.id], {
            limit: 5,
            transform: true,
            application: $scope.pipeline.application,
          })
          .then((executions) => {
            executions.forEach((execution) => ExecutionsTransformer.addBuildInfo(execution));
            $scope.pipelineExecutions = executions;
            if ($scope.plan && $scope.plan.executionId) {
              $scope.currentExecution = _.find($scope.pipelineExecutions, { id: $scope.plan.executionId });
            } else if ($location.search().executionId) {
              $scope.currentExecution = _.find($scope.pipelineExecutions, { id: $location.search().executionId });
            } else {
              $scope.currentExecution = $scope.pipelineExecutions[0];
            }
          })
          .catch(() => ($scope.pipelineExecutions = []));
      };

      this.revertPipelineChanges = () => {
        $scope.$applyAsync(() => {
          const original = getOriginal();
          Object.keys($scope.pipeline).forEach((key) => {
            delete $scope.pipeline[key];
          });
          Object.assign($scope.pipeline, original);

          if ($scope.isTemplatedPipeline) {
            const originalRenderablePipeline = getOriginalRenderablePipeline();
            Object.assign($scope.renderablePipeline, originalRenderablePipeline);
            Object.keys($scope.renderablePipeline).forEach((key) => {
              if (!originalRenderablePipeline.hasOwnProperty(key)) {
                delete $scope.renderablePipeline[key];
              }
            });
          } else {
            $scope.renderablePipeline = $scope.pipeline;
          }

          // if we were looking at a stage that no longer exists, move to the last stage
          if ($scope.viewState.section === 'stage') {
            const lastStage = $scope.renderablePipeline.stages.length - 1;
            if ($scope.viewState.stageIndex > lastStage) {
              this.setViewState({ stageIndex: lastStage });
            }
            if (!$scope.renderablePipeline.stages.length) {
              this.navigateTo({ section: 'triggers' });
            }
          }
          $scope.viewState.revertCount = ($scope.viewState.revertCount || 0) + 1;
          $scope.$broadcast('pipeline-reverted');
        });
      };

      // Poor bridge to update dirty flag when React stage field is updated
      this.stageFieldUpdated = () => markDirty();

      function cacheViewState() {
        const toCache = { section: $scope.viewState.section, stageIndex: $scope.viewState.stageIndex };
        configViewStateCache.put(buildCacheKey(), toCache);
      }

      $scope.$watch('pipeline', markDirty, true);
      $scope.$watch('viewState.original', markDirty);
      $scope.$watchGroup(['viewState.section', 'viewState.stageIndex'], cacheViewState);

      this.navigateTo({ section: $scope.viewState.section, index: $scope.viewState.stageIndex });

      this.getUrl = () => {
        return $location.absUrl();
      };

      const warningMessage = 'You have unsaved changes.\nAre you sure you want to navigate away from this page?';

      const confirmPageLeave = $scope.$on('$stateChangeStart', (event) => {
        if ($scope.viewState.isDirty) {
          if (!$window.confirm(warningMessage)) {
            event.preventDefault();
          }
        }
      });

      const validationSubscription = PipelineConfigValidator.subscribe((validations) => {
        this.validations = validations;
        this.preventSave = validations.preventSave;
      });

      $window.onbeforeunload = () => {
        if ($scope.viewState.isDirty) {
          return warningMessage;
        }
      };

      $scope.$on('$destroy', () => {
        confirmPageLeave();
        validationSubscription.unsubscribe();
        $window.onbeforeunload = undefined;
      });

      if ($scope.hasDynamicSource) {
        this.getPipelineExecutions();
      }

      if ($scope.isTemplatedPipeline && $scope.pipeline.isNew && !$scope.hasDynamicSource) {
        this.configureTemplate();
      }

      this.saveTemplate = (template) => {
        return PipelineTemplateWriter.savePipelineTemplateV2(template).then(
          (response) => {
            const id = response.variables.find((v) => v.key === 'pipelineTemplate.id').value;
            $state.go('home.pipeline-templates.pipeline-templates-detail', {
              templateId: PipelineTemplateV2Service.idForTemplate({ id }),
            });
            return true;
          },
          (err) => {
            throw err;
          },
        );
      };

      //update pipeline through a callback for React
      this.updatePipelineConfig = (changes) => {
        $scope.pipeline = Object.assign(
          $scope.pipeline,
          $scope.isV2TemplatedPipeline
            ? PipelineTemplateV2Service.filterInheritedConfig(Object.assign({}, changes))
            : changes,
        );

        if ($scope.isV2TemplatedPipeline) {
          $scope.renderablePipeline = Object.assign($scope.renderablePipeline, changes);
        } else if (!$scope.isTemplatedPipeline) {
          $scope.renderablePipeline = $scope.pipeline;
        }

        markDirty();
      };
    },
  ]);
