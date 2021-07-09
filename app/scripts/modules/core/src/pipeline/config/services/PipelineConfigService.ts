import { cloneDeep, sortBy, uniq } from 'lodash';
import { $q } from 'ngimport';

import { REST } from '../../../api/ApiService';
import { AuthenticationService } from '../../../authentication/AuthenticationService';
import { ViewStateCache } from '../../../cache';
import { IPipeline } from '../../../domain/IPipeline';
import { IStage } from '../../../domain/IStage';

import { PipelineTemplateV2Service } from '../templates/v2/pipelineTemplateV2.service';

export interface ITriggerPipelineResponse {
  eventId: string;
  ref: string;
}
export class PipelineConfigService {
  private static configViewStateCache = ViewStateCache.createCache('pipelineConfig', { version: 2 });

  private static buildViewStateCacheKey(applicationName: string, pipelineName: string): string {
    return `${applicationName}:${pipelineName}`;
  }

  public static getPipelinesForApplication(applicationName: string): PromiseLike<IPipeline[]> {
    return REST('/applications')
      .path(applicationName, 'pipelineConfigs')
      .get()
      .then((pipelines: IPipeline[]) => {
        pipelines.forEach((p) => (p.stages = p.stages || []));
        return this.sortPipelines(pipelines);
      });
  }

  public static getStrategiesForApplication(applicationName: string): PromiseLike<IPipeline[]> {
    return REST('/applications')
      .path(applicationName, 'strategyConfigs')
      .get()
      .then((pipelines: IPipeline[]) => {
        pipelines.forEach((p) => (p.stages = p.stages || []));
        return this.sortPipelines(pipelines);
      });
  }

  public static getHistory(id: string, isStrategy: boolean, count = 20): PromiseLike<IPipeline[]> {
    const endpoint = isStrategy ? 'strategyConfigs' : 'pipelineConfigs';
    return REST(endpoint).path(id, 'history').query({ limit: count }).get();
  }

  public static deletePipeline(applicationName: string, pipeline: IPipeline, pipelineName: string): PromiseLike<void> {
    const endpoint = pipeline.strategy ? 'strategies' : 'pipelines';
    return REST(endpoint).path(applicationName, pipelineName.trim()).delete();
  }

  public static savePipeline(toSave: IPipeline): PromiseLike<void> {
    let pipeline = cloneDeep(toSave);
    delete pipeline.isNew;
    pipeline.name = pipeline.name.trim();
    if (Array.isArray(pipeline.stages)) {
      pipeline.stages.forEach(function (stage) {
        delete stage.isNew;
        if (!stage.name) {
          delete stage.name;
        }
      });
    }
    if (PipelineTemplateV2Service.isV2PipelineConfig(pipeline)) {
      pipeline = PipelineTemplateV2Service.filterInheritedConfig(pipeline) as IPipeline;
    }

    const endpoint = pipeline.strategy ? 'strategies' : 'pipelines';
    return REST(endpoint).query({ staleCheck: true }).post(pipeline);
  }

  public static reorderPipelines(
    application: string,
    idsToIndices: { [key: string]: number },
    isStrategy = false,
  ): PromiseLike<void> {
    const type = isStrategy ? 'strategies' : 'pipelines';
    return REST('/actions').path(type, 'reorder').post({
      application,
      idsToIndices,
    });
  }

  public static renamePipeline(
    applicationName: string,
    pipeline: IPipeline,
    currentName: string,
    newName: string,
  ): PromiseLike<void> {
    this.configViewStateCache.remove(this.buildViewStateCacheKey(applicationName, currentName));
    pipeline.name = newName.trim();
    const endpoint = pipeline.strategy ? 'strategies' : 'pipelines';
    return REST(endpoint).path(pipeline.id).put(pipeline);
  }

  public static triggerPipeline(applicationName: string, pipelineName: string, body: any = {}): PromiseLike<string> {
    body.user = AuthenticationService.getAuthenticatedUser().name;
    return REST('/pipelines/v2')
      .path(applicationName, pipelineName)
      .post(body)
      .then((result: ITriggerPipelineResponse) => {
        return result.ref.split('/').pop();
      });
  }

  public static getDownstreamStageIds(pipeline: IPipeline, stage: IStage): Array<string | number> {
    let downstream: Array<string | number> = [];
    const children = pipeline.stages.filter((stageToTest: IStage) => {
      return stageToTest.requisiteStageRefIds && stageToTest.requisiteStageRefIds.includes(stage.refId);
    });
    if (children.length) {
      downstream = children.map((c) => c.refId);
      children.forEach((child) => {
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
        stage.requisiteStageRefIds &&
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

  private static sortPipelines(pipelines: IPipeline[]): PromiseLike<IPipeline[]> {
    const sorted = sortBy(pipelines, ['index', 'name']);

    // if there are pipelines with a bad index, fix that
    const toReindex: Array<PromiseLike<void>> = [];
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
