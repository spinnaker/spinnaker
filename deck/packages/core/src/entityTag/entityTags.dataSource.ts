import { EntityTagsReader } from './EntityTagsReader';
import { ApplicationDataSourceRegistry } from '../application';
import type { Application } from '../application/application.model';
import { SETTINGS } from '../config/settings';
import type { IEntityTags } from '../domain/IEntityTags';
import { noop } from '../utils';

export function registerEntityTagsDataSource(
  when: <T>(value: T | PromiseLike<T>) => PromiseLike<T> = <T>(value: T | PromiseLike<T>) => Promise.resolve(value),
): void {
  if (
    !SETTINGS.feature.entityTags ||
    ApplicationDataSourceRegistry.getDataSources().some((source) => source.key === 'entityTags')
  ) {
    return;
  }
  const loadEntityTags = (application: Application) => {
    return EntityTagsReader.getAllEntityTagsForApplication(application.name);
  };

  const addEntityTags = (_application: Application, data: IEntityTags[]) => {
    return when(data);
  };

  const addTagsToEntities = (application: Application) => {
    application
      .getDataSource('serverGroups')
      .ready()
      .then(() => EntityTagsReader.addTagsToServerGroups(application), noop);
    application
      .getDataSource('serverGroupManagers')
      .ready()
      .then(() => EntityTagsReader.addTagsToServerGroupManagers(application), noop);
    application
      .getDataSource('loadBalancers')
      .ready()
      .then(() => EntityTagsReader.addTagsToLoadBalancers(application), noop);
    application
      .getDataSource('securityGroups')
      .ready()
      .then(() => EntityTagsReader.addTagsToSecurityGroups(application), noop);
    application
      .getDataSource('executions')
      .ready()
      .then(() => EntityTagsReader.addTagsToExecutions(application), noop);
    application
      .getDataSource('pipelineConfigs')
      .ready()
      .then(() => EntityTagsReader.addTagsToPipelines(application), noop);
  };

  ApplicationDataSourceRegistry.registerDataSource({
    key: 'entityTags',
    visible: false,
    loader: loadEntityTags,
    onLoad: addEntityTags,
    afterLoad: addTagsToEntities,
    defaultData: [],
  });
}
