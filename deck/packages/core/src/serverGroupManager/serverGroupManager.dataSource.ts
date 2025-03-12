import type { IQService } from 'angular';
import { module } from 'angular';

import { ServerGroupManagerReader } from './ServerGroupManagerReader';
import type { Application } from '../application';
import { ApplicationDataSourceRegistry } from '../application';
import type { IServerGroupManager } from '../domain/IServerGroupManager';
import { EntityTagsReader } from '../entityTag/EntityTagsReader';

export const SERVER_GROUP_MANAGER_DATA_SOURCE = 'spinnaker.core.serverGroupManager.dataSource';
module(SERVER_GROUP_MANAGER_DATA_SOURCE, []).run([
  '$q',
  ($q: IQService) => {
    const loader = (application: Application) =>
      ServerGroupManagerReader.getServerGroupManagersForApplication(application.name);

    const onLoad = (_application: Application, serverGroupManagers: IServerGroupManager[]) =>
      $q.resolve(serverGroupManagers);

    const addTags = (application: Application) => {
      EntityTagsReader.addTagsToServerGroupManagers(application);
    };

    ApplicationDataSourceRegistry.registerDataSource({
      key: 'serverGroupManagers',
      visible: false,
      loader,
      onLoad,
      afterLoad: addTags,
      defaultData: [],
    });
  },
]);
