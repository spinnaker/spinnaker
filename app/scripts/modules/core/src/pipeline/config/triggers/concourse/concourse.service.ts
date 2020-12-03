import { API } from 'core/api/ApiService';

export class ConcourseService {
  public static listTeamsForMaster(master: string): PromiseLike<string[]> {
    return API.path('concourse', master, 'teams').get();
  }

  public static listPipelinesForTeam(master: string, team: string): PromiseLike<string[]> {
    return API.path('concourse', master, 'teams', team, 'pipelines').get();
  }

  public static listJobsForPipeline(master: string, team: string, pipeline: string): PromiseLike<string[]> {
    return API.path('concourse', master, 'teams', team, 'pipelines', pipeline, 'jobs').get();
  }

  public static listResourcesForPipeline(master: string, team: string, pipeline: string): PromiseLike<string[]> {
    return API.path('concourse', master, 'teams', team, 'pipelines', pipeline, 'resources').get();
  }
}
