import { REST } from '../../api';
import type { IProject, IProjectCluster } from '../../domain';

export class ProjectReader {
  public static listProjects(): Promise<IProject[]> {
    return REST('/projects').get();
  }

  public static getProjectConfig(projectName: string): Promise<IProject> {
    return REST('/projects').path(projectName).get();
  }

  public static getProjectClusters(projectName: string): Promise<IProjectCluster[]> {
    return REST('/projects').path(projectName, 'clusters').get();
  }
}
