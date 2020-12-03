import { API } from 'core/api';
import { IProject, IProjectCluster } from 'core/domain';

export class ProjectReader {
  public static listProjects(): PromiseLike<IProject[]> {
    return API.path('projects').get();
  }

  public static getProjectConfig(projectName: string): PromiseLike<IProject> {
    return API.path('projects', projectName).get();
  }

  public static getProjectClusters(projectName: string): PromiseLike<IProjectCluster[]> {
    return API.path('projects', projectName).path('clusters').get();
  }
}
