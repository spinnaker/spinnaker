import { IPromise } from 'angular';

import { API } from 'core/api';
import { SchedulerFactory } from 'core/scheduler';
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
  public static listApplications(): IPromise<IApplicationSummary[]> {
    return API.all('applications')
      .useCache()
      .getList();
  }

  public static getApplicationAttributes(name: string): IPromise<any> {
    return API.one('applications', name)
      .withParams({ expand: false })
      .get()
      .then((fromServer: Application) => {
        this.splitAttributes(fromServer.attributes, ['accounts', 'cloudProviders']);
        return fromServer.attributes;
      });
  }

  public static getApplication(name: string, expand = true): IPromise<Application> {
    return API.one('applications', name)
      .withParams({ expand: expand })
      .get()
      .then((fromServer: Application) => {
        const configs: IDataSourceConfig[] = ApplicationDataSourceRegistry.getDataSources();
        const application: Application = new Application(fromServer.name, SchedulerFactory.createScheduler(), configs);
        application.attributes = fromServer.attributes;
        this.splitAttributes(application.attributes, ['accounts', 'cloudProviders']);
        this.setDisabledDataSources(application);
        application.refresh();
        return application;
      });
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

  public static setDisabledDataSources(application: Application) {
    const allDataSources: ApplicationDataSource[] = application.dataSources;
    const appDataSources: IApplicationDataSourceAttribute = application.attributes.dataSources;

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
    allDataSources
      .filter(ds => ds.requiresDataSource)
      .forEach(ds => {
        const parent = allDataSources.find(p => p.key === ds.requiresDataSource);
        if (parent) {
          this.setDataSourceDisabled(ds, application, parent.disabled);
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
