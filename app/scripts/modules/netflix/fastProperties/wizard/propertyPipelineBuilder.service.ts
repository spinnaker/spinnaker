import { module } from 'angular';
import {PropertyCommand} from '../domain/propertyCommand.model';
import {PropertyPipelineStage} from '../domain/propertyPipelineStage';
import {IStage} from 'core/domain/IStage';
import {PropertyPipeline} from '../domain/propertyPipeline.domain';
import {AUTHENTICATION_SERVICE, AuthenticationService, IUser} from 'core/authentication/authentication.service';
import {PIPELINE_CONFIG_SERVICE, PipelineConfigService} from 'core/pipeline/config/services/pipelineConfig.service';
import IQService = angular.IQService;
import IPromise = angular.IPromise;
import {IPipeline} from 'core/domain/IPipeline';

export class PropertyPipelineBuilderService {

  static get $inject() {
    return [
      '$q',
      'authenticationService',
      'pipelineConfigService'
    ];
  }

  constructor(
    private $q: IQService,
    private authenticationService: AuthenticationService,
    private pipelineConfigService: PipelineConfigService) {}

  public build(command: PropertyCommand): IPromise<PropertyPipeline> {
    let user: IUser = this.authenticationService.getAuthenticatedUser();
    command.user = user;

    let propertyStage: PropertyPipelineStage = this.buildPropertyStage(user, command);

    let strategyStage: IStage = command.strategy.buildStage(user, command, propertyStage);

    let stages: IStage[] = [propertyStage, strategyStage].filter((stage) => !!stage);

    return this.getPipelineConfigId(command)
      .then((pipelineConfigId: string) => {
        return this.createPropertyPipeline(stages, pipelineConfigId);
      });
  }

  private buildPropertyStage(user: IUser, command: PropertyCommand): PropertyPipelineStage {
      return new PropertyPipelineStage(user, command);
  }

  private createPropertyPipeline(stages: IStage[], pipelineConfigId: string): PropertyPipeline {
    let pipeline = new PropertyPipeline(pipelineConfigId);
    pipeline.keepWaitingPipelines = false;
    pipeline.lastModifiedBy = 'spinnaker';
    pipeline.limitConcurrent = false;
    pipeline.parallel = true;
    pipeline.stages = stages;
    return pipeline;
  }

  private getPipelineConfigId(command: PropertyCommand): IPromise<string> {
    const spinnakerFPDummyAppPipelineConfigId = '4399e00d-3749-4418-b432-e65f9000457f';
    return command.applicationName ? this.getPipelineConfigForApplication(command.applicationName) : this.$q.when(spinnakerFPDummyAppPipelineConfigId);
  }

  private getPipelineConfigForApplication(appId: string): IPromise<string> {
    const fastPropertyPipelineName = '_fp_migrations_';

    return this.findPipelineByNameForApplication(fastPropertyPipelineName, appId)
      .then((foundPipeline: IPipeline) => foundPipeline.id)
      .catch(() => {
        let config: IPipeline = <IPipeline>{
          id: null,
          name: fastPropertyPipelineName,
          application: appId,
          stages: [],
          isNew: true,
          index: null,
          strategy: null,
          triggers: [],
          limitConcurrent: false,
          keepWaitingPipelines: true,
          parallel: true,
          executionEngine: 'v2',
          parameterConfig: []
        };
        return this.pipelineConfigService.savePipeline(config)
          .then(() => {
            return this.findPipelineByNameForApplication(fastPropertyPipelineName, appId);
          })
          .then((foundPipeline: IPipeline) => foundPipeline.id);
      });
  };

  private findPipelineByNameForApplication(pipelineName: string, appName: string) {
    return this.pipelineConfigService.getPipelinesForApplication(appName)
      .then((pipelines: IPipeline[]) => {
        let foundPipeline = pipelines.find((pipeline: IPipeline) => pipeline.name === pipelineName);
        return foundPipeline ? foundPipeline : this.$q.reject(`pipeline not found`);
      });
  }

}

export const FAST_PROPERTY_PIPELINE_BUILDER_SERVICE = 'spinnaker.netflix.fastproperty.pipeline.builder.service';

module(FAST_PROPERTY_PIPELINE_BUILDER_SERVICE, [
  AUTHENTICATION_SERVICE,
  PIPELINE_CONFIG_SERVICE
])
  .service('propertyPipelineBuilderService', PropertyPipelineBuilderService);
