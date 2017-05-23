import { IFilterService, ILogService, IPromise, IQService, module } from 'angular';

import { Api, API_SERVICE } from 'core/api/api.service';
import { SCHEDULER_FACTORY, SchedulerFactory } from 'core/scheduler/scheduler.factory';
import { Application } from '../application.model';
import { ApplicationDataSource, DataSourceConfig } from '../service/applicationDataSource';
import { APPLICATION_DATA_SOURCE_REGISTRY, ApplicationDataSourceRegistry } from './applicationDataSource.registry';
import { ROBOT_TO_HUMAN_FILTER } from 'core/presentation/robotToHumanFilter/robotToHuman.filter';

export interface IApplicationDataSourceAttribute {
  enabled: string[];
  disabled: string[];
}

export interface IApplicationSummary {
  name: string;
  email?: string;
  accounts?: string;
  updateTs?: string;
  createTs?: string;
  cloudProviders?: string;
}

export class ApplicationReader {

  public constructor(private $q: IQService, private $log: ILogService, private $filter: IFilterService,
                     private API: Api, private schedulerFactory: SchedulerFactory,
                     private applicationDataSourceRegistry: ApplicationDataSourceRegistry) {
    'ngInject';
  }

  public listApplications(): IPromise<IApplicationSummary[]> {
    return this.API.all('applications').useCache().getList();
  }

  public getApplication(name: string): IPromise<Application> {
    return this.API.one('applications', name).get().then((fromServer: Application) => {
      const application: Application = new Application(fromServer.name, this.schedulerFactory.createScheduler(), this.$q, this.$log);
      application.attributes = fromServer.attributes;
      this.splitAttributes(application.attributes, ['accounts', 'cloudProviders']);
      this.addDataSources(application);
      application.refresh();
      return application;
    });
  }

  private splitAttributes(attributes: any, fields: string[]) {
    fields.forEach(field => {
      if (attributes[field]) {
        if (!Array.isArray(attributes[field])) {
          attributes[field] = attributes[field].split(',');
        }
      } else {
        attributes[field] = [];
      }
    });
  }

  private addDataSources(application: Application): void {
    const dataSources: DataSourceConfig[] = this.applicationDataSourceRegistry.getDataSources();
    dataSources.forEach((ds: DataSourceConfig) => {
      const dataSource: ApplicationDataSource = new ApplicationDataSource(new DataSourceConfig(ds), application, this.$q, this.$log, this.$filter);
      application.dataSources.push(dataSource);
      application[ds.key] = dataSource;
    });
    this.setDisabledDataSources(application);
  }

  private setDisabledDataSources(application: Application) {
    const allDataSources: ApplicationDataSource[] = application.dataSources,
          appDataSources: IApplicationDataSourceAttribute = application.attributes.dataSources;
    if (!appDataSources) {
      allDataSources.filter(ds => ds.optIn).forEach(ds => this.disableDataSource(ds, application));
    } else {
      allDataSources.forEach(ds => {
        if (ds.optional) {
          if (ds.optIn && !appDataSources.enabled.includes(ds.key)) {
            this.disableDataSource(ds, application);
          }
          if (!ds.optIn && appDataSources.disabled.includes(ds.key)) {
            this.disableDataSource(ds, application);
          }
        }
      });
    }
  }

  private disableDataSource(dataSource: ApplicationDataSource, application: Application) {
    dataSource.disabled = true;
    if (dataSource.badge) {
      application.dataSources.find(ds => ds.key === dataSource.badge).disabled = true;
    }
  }
}

export const APPLICATION_READ_SERVICE = 'spinnaker.core.application.read.service';

module(APPLICATION_READ_SERVICE, [
  SCHEDULER_FACTORY,
  API_SERVICE,
  APPLICATION_DATA_SOURCE_REGISTRY,
  ROBOT_TO_HUMAN_FILTER,
]).service('applicationReader', ApplicationReader);
