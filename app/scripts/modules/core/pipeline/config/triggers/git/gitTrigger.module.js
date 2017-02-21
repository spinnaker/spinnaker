'use strict';

import _ from 'lodash';
import {SERVICE_ACCOUNT_SERVICE} from 'core/serviceAccount/serviceAccount.service.ts';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.trigger.git', [
    require('core/config/settings.js'),
    SERVICE_ACCOUNT_SERVICE,
  ])
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerTrigger({
      label: 'Git',
      description: 'Executes the pipeline on a git push',
      key: 'git',
      controller: 'GitTriggerCtrl',
      controllerAs: 'vm',
      templateUrl: require('./gitTrigger.html'),
      popoverLabelUrl: require('./gitPopoverLabel.html'),
      validators: [
        {
          type: 'serviceAccountAccess',
          message: `You do not have access to the service account configured in this pipeline's git trigger.
                    You will not be able to save your edits to this pipeline.`,
          preventSave: true,
        }
      ]
    });
  })
  .controller('GitTriggerCtrl', function (trigger, $scope, settings, serviceAccountService) {
    this.trigger = trigger;
    this.fiatEnabled = settings.feature.fiatEnabled;

    serviceAccountService.getServiceAccounts().then(accounts => {
      this.serviceAccounts = accounts || [];
    });
    $scope.gitTriggerTypes = ['stash', 'github', 'bitbucket'];

    if (settings && settings.gitSources) {
      $scope.gitTriggerTypes = settings.gitSources;
    }

    if ($scope.gitTriggerTypes.length == 1) {
      trigger.source = $scope.gitTriggerTypes[0];
    }

    function updateBranch() {
      if (_.trim(trigger.branch) === '') {
        trigger.branch = null;
      }
    }

    $scope.displayText = {
      'pipeline.config.git.project': {
        'bitbucket': 'Team or User',
        'github': 'Organization or User',
        'stash': 'Project',
      },
      'pipeline.config.git.slug': {
        'bitbucket': 'Repo name',
        'github': 'Project',
        'stash': 'Repo name'
      },
      'vm.trigger.project': {
        'bitbucket': 'Team or User name, i.e. spinnaker for bitbucket.org/spinnaker/echo',
        'github': 'Organization or User name, i.e. spinnaker for github.com/spinnaker/echo',
        'stash': 'Project name, i.e. SPKR for stash.mycorp.com/projects/SPKR/repos/echo'
      },
      'vm.trigger.slug': {
        'bitbucket': 'Repository name (not the url), i.e, echo for bitbucket.org/spinnaker/echo',
        'github': 'Project name (not the url), i.e, echo for github.com/spinnaker/echo',
        'stash': 'Repository name (not the url), i.e, echo for stash.mycorp.com/projects/SPKR/repos/echo'
      }
    };

    $scope.$watch('trigger.branch', updateBranch);
  });
