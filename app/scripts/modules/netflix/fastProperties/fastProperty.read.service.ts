import { module, IPromise, IQService, ITimeoutService } from 'angular';

import { API_SERVICE, Api } from 'core/api/api.service';
import { Scope } from './domain/scope.domain';
import { IExecution, IPipeline } from 'core/domain';
import { ExecutionService, EXECUTION_SERVICE } from 'core/delivery/service/execution.service';
import { PipelineConfigService, PIPELINE_CONFIG_SERVICE } from 'core/pipeline/config/services/pipelineConfig.service';
import { Property } from './domain/property.domain';
import { IPropertyHistoryEntry } from './domain/propertyHistory.domain';
import { jsonUtilityService } from 'core/utils/json/json.utility.service';
import { Application } from 'core/application/application.model';

export class FastPropertyReaderService {

  public get fastPropertyPipelineName() { return '_fp_migrations_'; }

  // local cache for pipeline config ids
  private applicationConfigMap: { [app: string]: string } = {};

  constructor(private $q: IQService, private API: Api, private executionService: ExecutionService,
              private pipelineConfigService: PipelineConfigService, private $timeout: ITimeoutService) {
    'ngInject';
  }

  public fetchForAppName(appName: string): IPromise<Property[]> {
    return this.API.all('fastproperties').all('application').one(appName).get()
      .then((data: any) => this.filterOldProperties(data.propertiesList));
  }

  public search(searchTerm: string): IPromise<Property[]> {
    return this.API.all('fastproperties').all('search').one(searchTerm).get()
      .then((data: any) => this.filterOldProperties(data.propertiesList));
  }

  public getPropByIdAndEnv(id: string, env: string): IPromise<Property> {
    return this.API.all('fastproperties').one('id', id).one('env', env).get()
      .then((data: any) => Property.from(data.property));
  }

  public fetchImpactCountForScope(fastPropertyScope: Scope): IPromise<any> {
    return this.API.all('fastproperties').all('impact').post(fastPropertyScope);
  }

  public getHistory(id: string, env: string): IPromise<IPropertyHistoryEntry[]> {
    return this.API.all('fastproperties').all('history').one(env, id).getList()
      .then((data: IPropertyHistoryEntry[]) => data
        .sort((a, b) => b.timestamp - a.timestamp)
        .map((entry, idx) => {
          try {
            const parsedComment: any = JSON.parse(entry.comment || '{}');
            entry.comment = parsedComment.comment as string;
            if (idx + 1 < data.length) {
              const right: string = JSON.stringify(JSON.parse(parsedComment.value), null, 2),
                    left: any = JSON.stringify(JSON.parse(JSON.parse(data[idx + 1].comment).value), null, 2);
              entry.diff = jsonUtilityService.diff(left, right);
            }
          } catch (ignored) {}
          return entry;
      }));
  }

  public addFastPropertiesToApplication(application: Application, properties: Property[]): Property[] {
    if (application.properties.data) {
      const data: Property[] = application.properties.data;
      // remove any that have dropped off, update any that have changed
      const toRemove: number[] = [];
      data.forEach((property: Property, idx: number) => {
        if (!properties.find(p => p.stringVal === property.stringVal)) {
          toRemove.push(idx);
        }
      });
      toRemove.reverse().forEach(idx => data.splice(idx, 1));

      properties.forEach(property => {
        if (!data.find(p => p.stringVal === property.stringVal)) {
          data.push(property);
        }
      });
      return data;
    } else {
      return properties;
    }
  }

  public getPipelineConfigForApplication(appId: string): IPromise<string> {
    if (this.applicationConfigMap[appId]) {
      return this.$q.when(this.applicationConfigMap[appId]);
    }
    return this.findPipelineByNameForApplication(appId)
      .then((foundPipeline: IPipeline) => foundPipeline.id)
      .catch(() => {
        const config: IPipeline = <IPipeline>{
          id: null,
          application: this.fastPropertyPipelineName,
          name: appId,
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
            return this.findPipelineByNameForApplication(appId);
          })
          .then((foundPipeline: IPipeline) => foundPipeline.id);
      });
  };

  public getPipelineConfigId(applicationName: string): IPromise<string> {
    const spinnakerFPDummyAppPipelineConfigId = '4399e00d-3749-4418-b432-e65f9000457f';
    return applicationName ? this.getPipelineConfigForApplication(applicationName) : this.$q.when(spinnakerFPDummyAppPipelineConfigId);
  }

  public getPromotionsForApplication(applicationName: string, statuses?: string[]): IPromise<IExecution[]> {
    return this.getPipelineConfigForApplication(applicationName).then(
      (pipelineConfigId: string) => {
        return this.executionService.getExecutionsForConfigIds(null, pipelineConfigId, 100, statuses ? statuses.join(',') : null)
      }
    )
  }

  public waitForPromotionPipelineToAppear(application: Application, executionId: string, matcher?: (e: IExecution) => boolean): IPromise<IExecution> {
    matcher = matcher || (() => true);
    return this.getPromotionsForApplication(application.name, ['RUNNING', 'SUCCEEDED', 'TERMINAL']).then((executions: IExecution[]) => {
      const match = executions.find(e => e.id === executionId && matcher(e));
      const deferred = this.$q.defer<IExecution>();
      if (match) {
        if (!application.global) {
          application.getDataSource('propertyPromotions').refresh().then(() => deferred.resolve(match));
        } else {
          deferred.resolve(match);
        }
        return deferred.promise;
      } else {
        return this.$timeout(() => {
          return this.waitForPromotionPipelineToAppear(application, executionId, matcher);
        }, 2000);
      }
    });
  }

  private findPipelineByNameForApplication(appName: string) {
    return this.pipelineConfigService.getPipelinesForApplication(this.fastPropertyPipelineName)
      .then((pipelines: IPipeline[]) => {
        const foundPipeline = pipelines.find((pipeline: IPipeline) => pipeline.name === appName);
        if (foundPipeline) {
          this.applicationConfigMap[appName] = foundPipeline.id;
        }
        return foundPipeline ? foundPipeline : this.$q.reject(`pipeline not found`);
      });
  }

  private filterOldProperties(properties: any): Property[] {
    return properties.filter((p: any) => // filter duplicates that sometimes appear by taking the most recent
        !properties.some((p2: any) => p !== p2 && p2.propertyId === p.propertyId && p.ts > p2.ts))
        .map((p: any) => Property.from(p));
  }

}

export const FAST_PROPERTY_READ_SERVICE = 'spinnaker.netflix.fastProperties.read.service';

module(FAST_PROPERTY_READ_SERVICE, [
  API_SERVICE,
  EXECUTION_SERVICE,
  PIPELINE_CONFIG_SERVICE
])
  .service('fastPropertyReader', FastPropertyReaderService);
