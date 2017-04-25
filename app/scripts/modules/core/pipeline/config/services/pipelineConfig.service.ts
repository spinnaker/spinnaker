import {IPromise, IQService, module} from 'angular';
import {sortBy, uniq} from 'lodash';

import {API_SERVICE, Api} from 'core/api/api.service';
import {AUTHENTICATION_SERVICE, AuthenticationService} from 'core/authentication/authentication.service';
import {VIEW_STATE_CACHE_SERVICE, ViewStateCacheService} from 'core/cache/viewStateCache.service';
import {ICache} from 'core/cache/deckCache.service';
import {IStage} from 'core/domain/IStage';
import {IPipeline} from 'core/domain/IPipeline';

export interface ITriggerPipelineResponse {
  ref: string;
}
export class PipelineConfigService {

  private configViewStateCache: ICache;

  static get $inject() { return ['$q', 'API', 'authenticationService', 'viewStateCache']; }

  public constructor(private $q: IQService,
                     private API: Api,
                     private authenticationService: AuthenticationService,
                     viewStateCache: ViewStateCacheService) {
    this.configViewStateCache = viewStateCache.createCache('pipelineConfig', { version: 2 });
  }

  private buildViewStateCacheKey(applicationName: string, pipelineName: string): string {
    return `${applicationName}:${pipelineName}`;
  }

  public getPipelinesForApplication(applicationName: string): IPromise<IPipeline[]> {
    return this.API.one('applications').one(applicationName).all('pipelineConfigs').getList()
      .then((pipelines: IPipeline[]) => {
        pipelines.forEach(p => p.stages = p.stages || []);
        return this.sortPipelines(pipelines);
      });
  }

  public getStrategiesForApplication(applicationName: string): IPromise<IPipeline[]> {
    return this.API.one('applications').one(applicationName).all('strategyConfigs').getList()
      .then((pipelines: IPipeline[]) => {
      pipelines.forEach(p => p.stages = p.stages || []);
      return this.sortPipelines(pipelines);
    });
  }

  public getHistory(id: string, count = 20): IPromise<IPipeline[]> {
    return this.API.one('pipelineConfigs', id).all('history').withParams({count: count}).getList();
  }

  public deletePipeline(applicationName: string, pipeline: IPipeline, pipelineName: string): IPromise<void> {
    return this.API.one(pipeline.strategy ? 'strategies' : 'pipelines').one(applicationName, pipelineName).remove();
  }

  public savePipeline(pipeline: IPipeline): IPromise<void> {
    delete pipeline.isNew;
    pipeline.stages.forEach(function(stage) {
      delete stage.isNew;
      if (!stage.name) {
        delete stage.name;
      }
    });
    return this.API.one( pipeline.strategy ? 'strategies' : 'pipelines').data(pipeline).post();
  }

  public renamePipeline(applicationName: string, pipeline: IPipeline, currentName: string, newName: string): IPromise<void> {
    this.configViewStateCache.remove(this.buildViewStateCacheKey(applicationName, currentName));
    pipeline.name = newName;
    return this.API.one(pipeline.strategy ? 'strategies' : 'pipelines').one(pipeline.id).data(pipeline).put();
  }

  public triggerPipeline(applicationName: string, pipelineName: string, body: any = {}): IPromise<ITriggerPipelineResponse> {
    body.user = this.authenticationService.getAuthenticatedUser().name;
    return this.API.one('pipelines').one(applicationName).one(pipelineName).data(body).post();
  }

  public getDownstreamStageIds(pipeline: IPipeline, stage: IStage): (string | number)[] {
    let downstream: (string | number)[] = [];
    const children = pipeline.stages.filter((stageToTest: IStage) => {
      return stageToTest.requisiteStageRefIds &&
        stageToTest.requisiteStageRefIds.includes(stage.refId);
    });
    if (children.length) {
      downstream = children.map(c => c.refId);
      children.forEach((child) => {
        downstream = downstream.concat(this.getDownstreamStageIds(pipeline, child));
      });
    }
    return uniq(downstream);
  }

  public getDependencyCandidateStages(pipeline: IPipeline, stage: IStage): IStage[] {
    const downstreamIds: (string | number)[] = this.getDownstreamStageIds(pipeline, stage);
    return pipeline.stages.filter((stageToTest: IStage) => {
      return stage !== stageToTest &&
        stageToTest.requisiteStageRefIds &&
        !downstreamIds.includes(stageToTest.refId) &&
        !stage.requisiteStageRefIds.includes(stageToTest.refId);
    });
  }

  public getAllUpstreamDependencies(pipeline: IPipeline, stage: IStage): IStage[] {
    let upstreamStages: IStage[] = [];
    if (stage.requisiteStageRefIds && stage.requisiteStageRefIds.length) {
      pipeline.stages.forEach((stageToTest: IStage) => {
        if (stage.requisiteStageRefIds.includes(stageToTest.refId)) {
          upstreamStages.push(stageToTest);
          upstreamStages = upstreamStages.concat(this.getAllUpstreamDependencies(pipeline, stageToTest));
        }
      });
    }
    return uniq(upstreamStages);
  }

  public startAdHocPipeline(body: any): IPromise<void> {
    body.user = this.authenticationService.getAuthenticatedUser().name;
    return this.API.one('pipelines').one('start').data(body).post();
  }

  private sortPipelines(pipelines: IPipeline[]): IPromise<IPipeline[]> {

    const sorted = sortBy(pipelines, ['index', 'name']);

    // if there are pipelines with a bad index, fix that
    const toReindex: IPromise<void>[] = [];
    if (sorted && sorted.length) {
      sorted.forEach((pipeline, index) => {
        if (pipeline.index !== index) {
          pipeline.index = index;
          toReindex.push(this.savePipeline(pipeline));
        }
      });
      if (toReindex.length) {
        return this.$q.all(toReindex).then(() => sorted);
      }
    }
    return this.$q.resolve(sorted);
  }

}

export let pipelineConfigService: PipelineConfigService = undefined;
export const PIPELINE_CONFIG_SERVICE = 'spinnaker.core.pipeline.config.service';
module(PIPELINE_CONFIG_SERVICE, [
  API_SERVICE,
  AUTHENTICATION_SERVICE,
  VIEW_STATE_CACHE_SERVICE,
]).service('pipelineConfigService', PipelineConfigService)
  .run(($injector: any) => pipelineConfigService = <PipelineConfigService>$injector.get('pipelineConfigService'));
