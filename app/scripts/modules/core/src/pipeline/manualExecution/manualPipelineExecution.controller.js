'use strict';

const angular = require('angular');
import _ from 'lodash';

import { AuthenticationService } from 'core/authentication';
import { Registry } from 'core/registry';
import { SETTINGS } from 'core/config/settings';
import { AppNotificationsService } from 'core/notification/AppNotificationsService';

import { STAGE_MANUAL_COMPONENTS } from './stageManualComponents.component';
import { TRIGGER_TEMPLATE } from './triggerTemplate.component';

import './manualPipelineExecution.less';

module.exports = angular
  .module('spinnaker.core.pipeline.manualPipelineExecution.controller', [
    require('angular-ui-bootstrap'),
    TRIGGER_TEMPLATE,
    STAGE_MANUAL_COMPONENTS,
  ])
  .controller('ManualPipelineExecutionCtrl', function($scope, $uibModalInstance, pipeline, application, trigger) {
    let applicationNotifications = [];
    let pipelineNotifications = [];

    this.hiddenParameters = new Set();

    this.notificationTooltip = require('./notifications.tooltip.html');

    AppNotificationsService.getNotificationsForApplication(application.name).then(notifications => {
      Object.keys(notifications)
        .sort()
        .filter(k => Array.isArray(notifications[k]))
        .forEach(type => {
          notifications[type].forEach(notification => {
            applicationNotifications.push(notification);
          });
        });
      synchronizeNotifications();
    });

    let user = AuthenticationService.getAuthenticatedUser();

    let synchronizeNotifications = () => {
      this.notifications = applicationNotifications.concat(pipelineNotifications);
    };

    this.getNotifications = () => {
      return _.has(this.command, 'pipeline.notifications')
        ? this.command.pipeline.notifications.concat(applicationNotifications)
        : applicationNotifications;
    };

    let userEmail = user.authenticated && user.name.includes('@') ? user.name : null;

    this.command = {
      pipeline: pipeline,
      trigger: null,
      dryRun: false,
      notificationEnabled: false,
      notification: {
        type: 'email',
        address: userEmail,
        when: ['pipeline.complete', 'pipeline.failed'],
      },
    };

    this.dryRunEnabled = SETTINGS.feature.dryRunEnabled;

    // Poor react setState
    const updateCommand = () => {
      $scope.$applyAsync(() => {
        this.command = _.cloneDeep(this.command);
      });
    };

    let addTriggers = () => {
      let pipeline = this.command.pipeline;
      if (!pipeline || !pipeline.triggers || !pipeline.triggers.length) {
        this.triggers = null;
        this.command.trigger = null;
        return;
      }

      this.triggers = pipeline.triggers
        .filter(t => Registry.pipeline.hasManualExecutionComponentForTriggerType(t.type))
        .map(t => {
          let copy = _.clone(t);
          copy.description = '...'; // placeholder
          Registry.pipeline
            .getManualExecutionComponentForTriggerType(t.type)
            .formatLabel(t)
            .then(label => (copy.description = label));
          return copy;
        });

      if (trigger && trigger.type === 'manual' && this.triggers.length) {
        trigger.type = this.triggers[0].type;
      }

      const suppliedTriggerCanBeInvoked =
        trigger && Registry.pipeline.hasManualExecutionComponentForTriggerType(trigger.type);
      if (suppliedTriggerCanBeInvoked) {
        Registry.pipeline
          .getManualExecutionComponentForTriggerType(trigger.type)
          .formatLabel(trigger)
          .then(label => (trigger.description = label));
      }
      this.command.trigger = suppliedTriggerCanBeInvoked ? trigger : _.head(this.triggers);
    };

    /**
     * Controller API
     */

    this.triggerUpdated = trigger => {
      let command = this.command;

      if (trigger !== undefined) {
        command.trigger = trigger;
      }

      if (command.trigger && Registry.pipeline.hasManualExecutionComponentForTriggerType(command.trigger.type)) {
        this.triggerComponent = Registry.pipeline.getManualExecutionComponentForTriggerType(command.trigger.type);
      } else {
        this.triggerComponent = null;
      }
      updateCommand();
    };

    this.pipelineSelected = () => {
      const pipeline = this.command.pipeline,
        executions = application.executions.data || [];

      pipelineNotifications = pipeline.notifications || [];
      synchronizeNotifications();

      this.currentlyRunningExecutions = executions.filter(
        execution => execution.pipelineConfigId === pipeline.id && execution.isActive,
      );
      addTriggers();
      this.triggerUpdated();

      const additionalComponents = pipeline.stages.map(stage =>
        Registry.pipeline.getManualExecutionComponentForStage(stage),
      );
      this.stageComponents = _.uniq(_.compact(additionalComponents));

      if (pipeline.parameterConfig && pipeline.parameterConfig.length) {
        this.parameters = {};
        this.hasRequiredParameters = pipeline.parameterConfig.some(p => p.required);
        pipeline.parameterConfig.forEach(p => this.addParameter(p));
        this.updateParameters();
      }
    };

    this.addParameter = parameterConfig => {
      // Inject the default value into the options list if it is absent
      if (
        parameterConfig.default &&
        parameterConfig.options &&
        !parameterConfig.options.some(option => option.value === parameterConfig.default)
      ) {
        parameterConfig.options.unshift({ value: parameterConfig.default });
      }
      const { name } = parameterConfig;
      const parameters = trigger ? trigger.parameters : {};
      if (this.parameters[name] === undefined) {
        this.parameters[name] = parameters[name] !== undefined ? parameters[name] : parameterConfig.default;
      }
    };

    this.updateParameters = () => {
      this.command.pipeline.parameterConfig.forEach(p => {
        if (p.conditional) {
          const include = this.shouldInclude(p);
          if (!include) {
            delete this.parameters[p.name];
            this.hiddenParameters.add(p.name);
          } else {
            this.hiddenParameters.delete(p.name);
            this.addParameter(p);
          }
        }
      });
    };

    this.shouldInclude = p => {
      if (p.conditional) {
        const comparingTo = this.parameters[p.conditional.parameter];
        const value = p.conditional.comparatorValue;
        switch (p.conditional.comparator) {
          case '>':
            return parseFloat(comparingTo) > parseFloat(value);
          case '>=':
            return parseFloat(comparingTo) >= parseFloat(value);
          case '<':
            return parseFloat(comparingTo) < parseFloat(value);
          case '<=':
            return parseFloat(comparingTo) <= parseFloat(value);
          case '!=':
            return comparingTo !== value;
          case '=':
            return comparingTo === value;
        }
      }
      return true;
    };

    this.execute = () => {
      let selectedTrigger = this.command.trigger || {},
        command = {
          trigger: selectedTrigger,
        },
        pipeline = this.command.pipeline;

      if (this.command.notificationEnabled && this.command.notification.address) {
        selectedTrigger.notifications = [this.command.notification];
      }

      // include any extra data populated by trigger manual execution handlers
      angular.extend(selectedTrigger, this.command.extraFields);

      command.pipelineName = pipeline.name;
      selectedTrigger.type = 'manual';
      selectedTrigger.dryRun = this.command.dryRun;
      // The description is not part of the trigger spec, so don't send it
      delete selectedTrigger.description;

      if (pipeline.parameterConfig && pipeline.parameterConfig.length) {
        selectedTrigger.parameters = this.parameters;
      }
      $uibModalInstance.close(command);
    };

    this.cancel = $uibModalInstance.dismiss;

    this.hasStageOf = stageType => {
      return this.getStagesOf(stageType).length > 0;
    };

    this.getStagesOf = stageType => {
      if (!this.command.pipeline) {
        return [];
      }
      return this.command.pipeline.stages.filter(stage => stage.type === stageType);
    };

    /**
     * Initialization
     */

    if (pipeline) {
      this.pipelineSelected();
    }

    if (!pipeline) {
      this.pipelineOptions = application.pipelineConfigs.data.filter(c => !c.disabled);
    }
  });
