'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { AccountService, Registry } from '@spinnaker/core';

import { DcosProviderSettings } from '../../../dcos.settings';
import { DOCKER_IMAGE_AND_TAG_SELECTOR_COMPONENT } from './dockerImageAndTagSelector.component';
import { DCOS_JOB_GENERAL_COMPONENT } from '../../../job/general.component';
import { DCOS_JOB_LABELS_COMPONENT } from '../../../job/labels.component';

export const DCOS_PIPELINE_STAGES_RUNJOB_RUNJOBSTAGE = 'spinnaker.dcos.pipeline.stage.runJobStage';
export const name = DCOS_PIPELINE_STAGES_RUNJOB_RUNJOBSTAGE; // for backwards compatibility
module(DCOS_PIPELINE_STAGES_RUNJOB_RUNJOBSTAGE, [
  DCOS_JOB_GENERAL_COMPONENT,
  //TODO Add back when scheduled jobs are supported better by Spinnaker
  //require('dcos/job/schedule.component').name,
  DCOS_JOB_LABELS_COMPONENT,
  DOCKER_IMAGE_AND_TAG_SELECTOR_COMPONENT,
])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'runJob',
      cloudProvider: 'dcos',
      templateUrl: require('./runJobStage.html'),
      executionDetailsUrl: require('./runJobExecutionDetails.html'),
      validators: [
        { type: 'requiredField', fieldName: 'account' },
        { type: 'requiredField', fieldName: 'general.id' },
      ],
    });
  })
  .controller('dcosRunJobStageCtrl', [
    '$scope',
    '$q',
    function ($scope, $q) {
      const stage = $scope.stage;
      this.stage = $scope.stage;

      if (!_.has(stage, 'name')) {
        _.set(stage, 'name', Date.now().toString());
      }

      stage.cloudProvider = 'dcos';
      stage.application = $scope.application.name;

      if (!stage.credentials && $scope.application.defaultCredentials.dcos) {
        stage.credentials = $scope.application.defaultCredentials.dcos;
      }

      this.accountChanged = () => {
        setRegistry();
        this.updateRegions();
      };

      this.regionChanged = () => {};

      this.updateRegions = () => {
        if (stage.account) {
          $scope.stage.dcosClusters = $scope.backingData.credentialsKeyedByAccount[stage.account].dcosClusters;
          if ($scope.stage.dcosClusters.map((r) => r.name).every((r) => r !== stage.region)) {
            this.regionChanged();
            delete stage.region;
          }
        } else {
          $scope.stage.dcosClusters = null;
        }
      };

      this.onChange = (changes) => {
        stage.docker.image.registry = changes.registry;
      };

      function attemptToSetValidAccount(accountsByName, stage) {
        const defaultAccount = DcosProviderSettings.defaults.account;
        const dcosAccountNames = _.keys(accountsByName);
        let firstDcosAccount = null;
        if (dcosAccountNames.length) {
          firstDcosAccount = dcosAccountNames[0];
        }

        const defaultAccountIsValid = defaultAccount && dcosAccountNames.includes(defaultAccount);

        stage.account = defaultAccountIsValid
          ? defaultAccount
          : firstDcosAccount
          ? firstDcosAccount
          : 'my-dcos-account';

        attemptToSetValidDcosCluster(accountsByName, stage);
      }

      function attemptToSetValidDcosCluster(dcosAccountsByName, stage) {
        const defaultDcosCluster = DcosProviderSettings.defaults.dcosCluster;
        const selectedAccount = dcosAccountsByName[stage.account];

        if (selectedAccount) {
          const clusterNames = _.map(selectedAccount.dcosClusters, 'name');
          const defaultDcosClusterIsValid = defaultDcosCluster && clusterNames.includes(defaultDcosCluster);
          stage.dcosCluster = defaultDcosClusterIsValid
            ? defaultDcosCluster
            : clusterNames.length == 1
            ? clusterNames[0]
            : null;
          stage.region = stage.dcosCluster;
        }
      }

      function setRegistry() {
        if (stage.account) {
          _.set(
            stage,
            'docker.image.registry',
            $scope.backingData.credentialsKeyedByAccount[stage.account].dockerRegistries[0].accountName,
          );
        }
      }

      AccountService.getCredentialsKeyedByAccount('dcos').then((credentialsKeyedByAccount) => {
        $scope.backingData = {
          credentialsKeyedByAccount,
          accounts: Object.keys(credentialsKeyedByAccount),
        };

        if (!stage.account) {
          attemptToSetValidAccount(credentialsKeyedByAccount, stage);
        }

        setRegistry();
        this.updateRegions();
      });
    },
  ]);
