import { IPromise, module } from 'angular';
import { flatten, last } from 'lodash';

import { AUTHENTICATION_SERVICE, AuthenticationService, IStage, IUser } from '@spinnaker/core';

import { PropertyCommand } from '../domain/propertyCommand.model';
import { PropertyPipeline } from '../domain/propertyPipeline.domain';
import { PropertyPipelineStage } from '../domain/propertyPipelineStage';
import { FAST_PROPERTY_READ_SERVICE, FastPropertyReaderService } from '../fastProperty.read.service';

export class PropertyPipelineBuilderService {

  constructor( private authenticationService: AuthenticationService, private fastPropertyReader: FastPropertyReaderService) {
    'ngInject';
  }

  public build(command: PropertyCommand): IPromise<PropertyPipeline> {
    const user: IUser = this.authenticationService.getAuthenticatedUser();
    command.user = user;

    const propertyStage: PropertyPipelineStage[] = command.buildPropertyStages(user);
    const strategyStage: IStage = command.strategy.buildStage(user, command, last(propertyStage));
    const stages: IStage[] = flatten([propertyStage, strategyStage]).filter((stage) => !!stage);

    return this.fastPropertyReader.getPipelineConfigId(command.applicationName)
      .then((pipelineConfigId: string) => {
        return this.createPropertyPipeline(stages, pipelineConfigId, command.applicationName);
      });
  }

  private createPropertyPipeline(stages: IStage[], pipelineConfigId: string, applicationName: string): PropertyPipeline {
    const pipeline = new PropertyPipeline(pipelineConfigId);
    pipeline.keepWaitingPipelines = false;
    pipeline.lastModifiedBy = 'spinnaker';
    pipeline.limitConcurrent = false;
    pipeline.parallel = true;
    pipeline.stages = stages;
    pipeline.application = applicationName;
    pipeline.name = this.fastPropertyReader.fastPropertyPipelineName;
    return pipeline;
  }

}

export let propertyPipelineBuilderService: PropertyPipelineBuilderService = undefined;
export const FAST_PROPERTY_PIPELINE_BUILDER_SERVICE = 'spinnaker.netflix.fastproperty.pipeline.builder.service';

module(FAST_PROPERTY_PIPELINE_BUILDER_SERVICE, [
  AUTHENTICATION_SERVICE,
  FAST_PROPERTY_READ_SERVICE,
])
  .service('propertyPipelineBuilderService', PropertyPipelineBuilderService)
  .run(($injector: any) => propertyPipelineBuilderService = <PropertyPipelineBuilderService>$injector.get('propertyPipelineBuilderService'));
