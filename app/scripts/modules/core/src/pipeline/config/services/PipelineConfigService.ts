import { IPromise } from 'angular';
import { sortBy, uniq } from 'lodash';
import { $q } from 'ngimport';

import { API } from 'core/api/ApiService';
import { AuthenticationService } from 'core/authentication/AuthenticationService';
import { ViewStateCache } from 'core/cache';
import { IStage } from 'core/domain/IStage';
import { IPipeline } from 'core/domain/IPipeline';

export interface ITriggerPipelineResponse {
  ref: string;
}
export interface IEchoTriggerPipelineResponse {
  eventId: string;
}
export class PipelineConfigService {
  private static configViewStateCache = ViewStateCache.createCache('pipelineConfig', { version: 2 });

  private static buildViewStateCacheKey(applicationName: string, pipelineName: string): string {
    return `${applicationName}:${pipelineName}`;
  }

  public static getPipelinesForApplication(applicationName: string): IPromise<IPipeline[]> {
    return API.one('applications')
      .one(applicationName)
      .all('pipelineConfigs')
      .getList()
      .then((pipelines: IPipeline[]) => {
        pipelines.forEach(p => (p.stages = p.stages || []));
        return this.sortPipelines(pipelines);
      });
  }

  public static getStrategiesForApplication(applicationName: string): IPromise<IPipeline[]> {
    return API.one('applications')
      .one(applicationName)
      .all('strategyConfigs')
      .getList()
      .then((pipelines: IPipeline[]) => {
        pipelines.forEach(p => (p.stages = p.stages || []));
        return this.sortPipelines(pipelines);
      });
  }

  public static getHistory(id: string, isStrategy: boolean, count = 20): IPromise<IPipeline[]> {
    const endpoint = isStrategy ? 'strategyConfigs' : 'pipelineConfigs';
    return API.one(endpoint, id)
      .all('history')
      .withParams({ count })
      .getList();
  }

  public static deletePipeline(applicationName: string, pipeline: IPipeline, pipelineName: string): IPromise<void> {
    return API.one(pipeline.strategy ? 'strategies' : 'pipelines')
      .one(applicationName, encodeURIComponent(pipelineName.trim()))
      .remove();
  }

  public static savePipeline(pipeline: IPipeline): IPromise<void> {
    delete pipeline.isNew;
    pipeline.name = pipeline.name.trim();
    if (Array.isArray(pipeline.stages)) {
      pipeline.stages.forEach(function(stage) {
        delete stage.isNew;
        if (!stage.name) {
          delete stage.name;
        }
      });
    }
    return API.one(pipeline.strategy ? 'strategies' : 'pipelines')
      .data(pipeline)
      .post();
  }

  public static renamePipeline(
    applicationName: string,
    pipeline: IPipeline,
    currentName: string,
    newName: string,
  ): IPromise<void> {
    this.configViewStateCache.remove(this.buildViewStateCacheKey(applicationName, currentName));
    pipeline.name = newName.trim();
    return API.one(pipeline.strategy ? 'strategies' : 'pipelines')
      .one(pipeline.id)
      .data(pipeline)
      .put();
  }

  public static triggerPipeline(applicationName: string, pipelineName: string, body: any = {}): IPromise<string> {
    body.user = AuthenticationService.getAuthenticatedUser().name;
    return API.one('pipelines')
      .one(applicationName)
      .one(encodeURIComponent(pipelineName))
      .data(body)
      .post()
      .then((result: ITriggerPipelineResponse) => {
        return result.ref.split('/').pop();
      });
  }

  public static triggerPipelineViaEcho(
    applicationName: string,
    pipelineName: string,
    body: any = {},
  ): IPromise<string> {
    body.user = AuthenticationService.getAuthenticatedUser().name;
    return API.one('pipelines')
      .one('v2')
      .one(applicationName)
      .one(encodeURIComponent(pipelineName))
      .data(body)
      .post()
      .then((result: IEchoTriggerPipelineResponse) => {
        return result.eventId;
      });
  }

  public static getDownstreamStageIds(pipeline: IPipeline, stage: IStage): Array<string | number> {
    let downstream: Array<string | number> = [];
    const children = pipeline.stages.filter((stageToTest: IStage) => {
      return stageToTest.requisiteStageRefIds && stageToTest.requisiteStageRefIds.includes(stage.refId);
    });
    if (children.length) {
      downstream = children.map(c => c.refId);
      children.forEach(child => {
        downstream = downstream.concat(this.getDownstreamStageIds(pipeline, child));
      });
    }
    return uniq(downstream);
  }

  public static getDependencyCandidateStages(pipeline: IPipeline, stage: IStage): IStage[] {
    const downstreamIds: Array<string | number> = this.getDownstreamStageIds(pipeline, stage);
    return pipeline.stages.filter((stageToTest: IStage) => {
      return (
        stage !== stageToTest &&
        stageToTest.requisiteStageRefIds &&
        !downstreamIds.includes(stageToTest.refId) &&
        !stage.requisiteStageRefIds.includes(stageToTest.refId)
      );
    });
  }

  public static getAllUpstreamDependencies(pipeline: IPipeline, stage: IStage): IStage[] {
    if (!pipeline || !stage) {
      return [];
    }
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

  public static startAdHocPipeline(body: any): IPromise<string> {
    body.user = AuthenticationService.getAuthenticatedUser().name;
    return API.one('pipelines')
      .one('start')
      .data(body)
      .post()
      .then((result: ITriggerPipelineResponse) => {
        return result.ref.split('/').pop();
      });
  }

  private static sortPipelines(pipelines: IPipeline[]): IPromise<IPipeline[]> {
    const sorted = sortBy(pipelines, ['index', 'name']);

    // if there are pipelines with a bad index, fix that
    const toReindex: Array<IPromise<void>> = [];
    if (sorted && sorted.length) {
      sorted.forEach((pipeline, index) => {
        if (pipeline.index !== index) {
          pipeline.index = index;
          toReindex.push(this.savePipeline(pipeline));
        }
      });
      if (toReindex.length) {
        return $q.all(toReindex).then(() => sorted);
      }
    }
    return $q.resolve(sorted);
  }
}
