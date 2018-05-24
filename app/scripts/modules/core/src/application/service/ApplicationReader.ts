import { IPromise } from 'angular';
import { $q, $log, $filter } from 'ngimport';

import { API } from 'core/api/ApiService';
import { SchedulerFactory } from 'core/scheduler/SchedulerFactory';
import { Application } from '../application.model';
import { ApplicationDataSource, IDataSourceConfig } from '../service/applicationDataSource';
import { ApplicationDataSourceRegistry } from './ApplicationDataSourceRegistry';
import { InferredApplicationWarningService } from './InferredApplicationWarningService';

export interface IApplicationDataSourceAttribute {
  enabled: string[];
  disabled: string[];
}

export interface IApplicationSummary {
  accounts?: string;
  aliases?: string;
  cloudProviders?: string;
  createTs?: string;
  description?: string;
  email?: string;
  name: string;
  pdApiKey?: string;
  updateTs?: string;
}

export class ApplicationReader {
  private static applicationMap: Map<string, IApplicationSummary> = new Map<string, IApplicationSummary>();

  public static listApplications(populateMap = false): IPromise<IApplicationSummary[]> {
    return API.all('applications')
      .useCache()
      .getList()
      .then((applications: IApplicationSummary[]) => {
        if (populateMap) {
          const tmpMap: Map<string, IApplicationSummary> = new Map<string, IApplicationSummary>();
          applications.forEach((application: IApplicationSummary) => tmpMap.set(application.name, application));
          this.applicationMap = tmpMap;
        }
        return applications;
      });
  }

  public static getApplication(name: string, expand = true): IPromise<Application> {
    return API.one('applications', name)
      .withParams({ expand: expand })
      .get()
      .then((fromServer: Application) => {
        const application: Application = new Application(fromServer.name, SchedulerFactory.createScheduler(), $q, $log);
        application.attributes = fromServer.attributes;
        this.splitAttributes(application.attributes, ['accounts', 'cloudProviders']);
        this.addDataSources(application);
        application.refresh();
        return application;
      });
  }

  public static getApplicationMap(): Map<string, IApplicationSummary> {
    return this.applicationMap;
  }

  private static splitAttributes(attributes: any, fields: string[]) {
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

  private static addDataSources(application: Application): void {
    const dataSources: IDataSourceConfig[] = ApplicationDataSourceRegistry.getDataSources();
    dataSources.forEach((ds: IDataSourceConfig) => {
      const dataSource: ApplicationDataSource = new ApplicationDataSource(ds, application, $q, $log, $filter);
      application.dataSources.push(dataSource);
      application[ds.key] = dataSource;
    });
    this.setDisabledDataSources(application);
  }

  public static setDisabledDataSources(application: Application) {
    const allDataSources: ApplicationDataSource[] = application.dataSources,
      appDataSources: IApplicationDataSourceAttribute = application.attributes.dataSources;
    if (!appDataSources) {
      allDataSources.filter(ds => ds.optIn).forEach(ds => this.setDataSourceDisabled(ds, application, true));
      if (InferredApplicationWarningService.isInferredApplication(application)) {
        allDataSources
          .filter(ds => ds.requireConfiguredApp)
          .forEach(ds => this.setDataSourceDisabled(ds, application, true));
      }
    } else {
      allDataSources.forEach(ds => {
        if (ds.optional) {
          if (ds.optIn) {
            this.setDataSourceDisabled(ds, application, !appDataSources.enabled.includes(ds.key));
          } else {
            this.setDataSourceDisabled(ds, application, appDataSources.disabled.includes(ds.key));
          }
        }
      });
    }
    allDataSources.filter(ds => ds.requiresDataSource).forEach(ds => {
      const parent = allDataSources.find(p => p.key === ds.requiresDataSource);
      if (parent && parent.disabled) {
        this.setDataSourceDisabled(ds, application, true);
      }
    });
  }

  private static setDataSourceDisabled(dataSource: ApplicationDataSource, application: Application, disabled: boolean) {
    dataSource.disabled = disabled;
    if (dataSource.badge) {
      application.dataSources.find(ds => ds.key === dataSource.badge).disabled = disabled;
    }
  }
}
