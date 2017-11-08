'use strict';

import _ from 'lodash';
import { PIPELINE_CONFIG_SERVICE } from '@spinnaker/core';
import { KUBERNETES_IMAGE_ID_FILTER } from 'kubernetes/presentation/imageId.filter';

const angular = require('angular');

module.exports = angular.module('spinnaker.kubernetes.pipeline.stage.runJobStage', [
  require('kubernetes/container/commands.component.js').name,
  require('kubernetes/container/arguments.component.js').name,
  require('kubernetes/container/environmentVariables.component.js').name,
  require('kubernetes/container/volumes.component.js').name,
  require('kubernetes/image/image.reader.js').name,
  require('./runJobExecutionDetails.controller.js').name,
  PIPELINE_CONFIG_SERVICE,
  KUBERNETES_IMAGE_ID_FILTER,
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      provides: 'runJob',
      cloudProvider: 'kubernetes',
      templateUrl: require('./runJobStage.html'),
      executionDetailsUrl: require('./runJobExecutionDetails.html'),
      defaultTimeoutMs: 2 * 60 * 60 * 1000, // 2 hours
      validators: [
        { type: 'requiredField', fieldName: 'account' },
        { type: 'requiredField', fieldName: 'namespace' },
      ]
    });
  }).controller('kubernetesRunJobStageCtrl', function($scope, accountService, kubernetesImageReader, pipelineConfigService, $filter) {

    this.stage = $scope.stage;
    this.pipeline = $scope.pipeline;
    this.stage.cloudProvider = 'kubernetes';
    this.stage.application = $scope.application.name;
    this.policies = ['ClusterFirst', 'Default', 'ClusterFirstWithHostNet'];
    this.containers = [];

    if (!_.has(this.stage, 'container.name')) {
      _.set(this.stage, 'container.name', 'job');
    }

    if (!this.stage.dnsPolicy) {
      this.stage.dnsPolicy = 'ClusterFirst';
    }

    if (!this.stage.credentials && $scope.application.defaultCredentials.kubernetes) {
      this.stage.credentials = $scope.application.defaultCredentials.kubernetes;
    }

    this.contextImages = pipelineConfigService.getAllUpstreamDependencies(this.pipeline, this.stage).map((stage) => {
      if (stage.type !== 'findImage' && stage.type !== 'bake') {
        return;
      }

      if (stage.type === 'findImage') {
        return {
          fromContext: true,
          fromFindImage: true,
          cluster: stage.cluster,
          pattern: stage.imageNamePattern,
          repository: stage.name,
          stageId: stage.refId
        };
      }

      return {
        fromContext: true,
        fromBake: true,
        repository: stage.ami_name,
        organization: stage.organization,
        stageId: stage.refId
      };
    }).filter(image => !!image);

    accountService.getUniqueAttributeForAllAccounts('kubernetes', 'namespaces')
      .then((namespaces) => {
        this.namespaces = namespaces;
      });

    accountService.listAccounts('kubernetes')
      .then((accounts) => {
        this.accounts = accounts;
      });


    this.searchImages = (query) => {
      kubernetesImageReader.findImages({
        provider: 'dockerRegistry',
        count: 50,
        q: query
      }).then((data) => {

        if (this.pipeline.triggers) {
          data = data.concat(this.pipeline.triggers.map((image) => {
            image.fromTrigger = true;
            return image;
          }));
        }

        if (this.contextImages) {
          data = data.concat(this.contextImages);
        }

        this.containers = _.map(data, (image) => {
          return {
            name: image.repository.replace(/_/g, '').replace(/[\/ ]/g, '-').toLowerCase(),
            imageDescription: {
              repository: image.repository,
              tag: image.tag,
              registry: image.registry,
              fromContext: image.fromContext,
              fromTrigger: image.fromTrigger,
              fromFindImage: image.fromFindImage,
              cluster: image.cluster,
              account: image.account,
              pattern: image.pattern,
              stageId: image.stageId,
              imageId: $filter('kubernetesImageId')(image),
            },
            imagePullPolicy: 'IFNOTPRESENT',
            account: image.accountName,
            requests: {
              memory: null,
              cpu: null,
            },
            limits: {
              memory: null,
              cpu: null,
            },
            ports: [{
              name: 'http',
              containerPort: 80,
              protocol: 'TCP',
              hostPort: null,
              hostIp: null,
            }],
            livenessProbe: null,
            readinessProbe: null,
            envVars: [],
            command: [],
            args: [],
            volumeMounts: [],
          };
        });

      });
    };

    this.groupByRegistry = (container) => {
      if (container.imageDescription) {
        if (container.imageDescription.fromContext) {
          return 'Find Image Result(s)';
        } else if (container.imageDescription.fromTrigger) {
          return 'Images from Trigger(s)';
        } else {
          return container.imageDescription.registry;
        }
      }
    };

  });
