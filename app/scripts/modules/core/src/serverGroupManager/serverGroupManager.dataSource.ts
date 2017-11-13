import { IQService, module } from 'angular';

import { Application, APPLICATION_DATA_SOURCE_REGISTRY, ApplicationDataSourceRegistry } from 'core/application';
import { SERVER_GROUP_MANAGER_SERVICE, ServerGroupManagerService } from './serverGroupManager.service';
import { IServerGroupManager } from 'core/domain/IServerGroupManager';

export const SERVER_GROUP_MANAGER_DATA_SOURCE = 'spinnaker.core.serverGroupManager.dataSource';
module(SERVER_GROUP_MANAGER_DATA_SOURCE, [
  APPLICATION_DATA_SOURCE_REGISTRY,
  SERVER_GROUP_MANAGER_SERVICE,
]).run(($q: IQService,
        applicationDataSourceRegistry: ApplicationDataSourceRegistry,
        serverGroupManagerService: ServerGroupManagerService) => {
  'ngInject';

  const loader = (application: Application) =>
    serverGroupManagerService.getServerGroupManagersForApplication(application.name);

  const onLoad = (_application: Application, serverGroupManagers: IServerGroupManager[]) =>
    $q.resolve(serverGroupManagers);

  applicationDataSourceRegistry.registerDataSource({
    key: 'serverGroupManagers',
    visible: false,
    loader,
    onLoad,
  });
});
