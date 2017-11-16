'use strict';

const angular = require('angular');
import { KUBERNETES_LIFECYCLE_HOOK_CONFIGURER } from 'kubernetes/container/lifecycleHook.component';

module.exports = angular.module('spinnaker.kubernetes.pipeline.stage.runJobStage.configure', [
  require('kubernetes/container/commands.component.js').name,
  require('kubernetes/container/arguments.component.js').name,
  require('kubernetes/container/environmentVariables.component.js').name,
  require('kubernetes/container/volumes.component.js').name,
  require('kubernetes/container/ports.component.js').name,
  require('kubernetes/container/resources.component.js').name,
  require('kubernetes/container/probe.directive.js').name,
  KUBERNETES_LIFECYCLE_HOOK_CONFIGURER
])
  .controller('kubernetesConfigureJobController', function($scope, $uibModalInstance, accountService, kubernetesImageReader, pipelineConfigService, $filter,
                                                           stage, pipeline, application) {

    this.stage = stage;
    this.pipeline = pipeline;
    this.policies = ['ClusterFirst', 'Default', 'ClusterFirstWithHostNet'];

    accountService.getUniqueAttributeForAllAccounts('kubernetes', 'namespaces')
      .then((namespaces) => {
        this.namespaces = namespaces;
      });

    accountService.listAccounts('kubernetes')
      .then((accounts) => {
        this.accounts = accounts;
      });


    if (!_.has(this.stage, 'container.name')) {
      _.set(this.stage, 'container.name', 'job');
    }

    if (!this.stage.dnsPolicy) {
      this.stage.dnsPolicy = 'ClusterFirst';
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

    this.setPostStartHandler = (handler) => {
      if (!this.stage.container.lifecycle) {
        this.stage.container.lifecycle = {};
      }
      this.stage.container.lifecycle.postStart = handler;
    };

    this.setPreStopHandler = (handler) => {
      if (!this.stage.container.lifecycle) {
        this.stage.container.lifecycle = {};
      }
      this.stage.container.lifecycle.preStop = handler;
    };

    this.submit = () => {
      $uibModalInstance.close(this.stage);
    };

    this.cancel = () => {
      $uibModalInstance.dismiss();
    };

  });
