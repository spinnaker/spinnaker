import {module} from 'angular';

import {APPLICATION_DATA_SOURCE_REGISTRY, ApplicationDataSourceRegistry} from './applicationDataSource.registry';
import {API_SERVICE, Api} from 'core/api/api.service';
import {ApplicationDataSource, DataSourceConfig} from '../service/applicationDataSource';
import {Application} from '../application.model';
import {SCHEDULER_FACTORY, SchedulerFactory} from 'core/scheduler/scheduler.factory';

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

  public constructor(private $q: ng.IQService, private $log: ng.ILogService, private $filter: ng.IFilterService,
                     private API: Api, private schedulerFactory: SchedulerFactory,
                     private applicationDataSourceRegistry: ApplicationDataSourceRegistry) {
    'ngInject';
  }

  public listApplications(): ng.IPromise<IApplicationSummary[]> {
    return this.API.all('applications').useCache().getList();
  }

  public getApplication(name: string): ng.IPromise<Application> {
    return this.API.one('applications', name).get().then((fromServer: Application) => {
      const application: Application = new Application(name, this.schedulerFactory.createScheduler(), this.$q, this.$log);
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
  require('../../presentation/robotToHumanFilter/robotToHuman.filter'),
  API_SERVICE,
  APPLICATION_DATA_SOURCE_REGISTRY,
]).service('applicationReader', ApplicationReader);
