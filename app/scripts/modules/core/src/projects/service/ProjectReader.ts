import { IPromise } from 'angular';

import { API } from 'core/api';
import { IProject, IProjectCluster } from 'core/domain';

export class ProjectReader {
  public static listProjects(): IPromise<IProject[]> {
    return API.all('projects').getList();
  }

  public static getProjectConfig(projectName: string): IPromise<IProject> {
    return API.one('projects', projectName).get();
  }

  public static getProjectClusters(projectName: string): IPromise<IProjectCluster[]> {
    return API.one('projects', projectName)
      .all('clusters')
      .getList();
  }
}
