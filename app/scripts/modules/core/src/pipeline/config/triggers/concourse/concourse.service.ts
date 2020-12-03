import { API } from 'core/api/ApiService';

export class ConcourseService {
  public static listTeamsForMaster(master: string): PromiseLike<string[]> {
    return API.path('concourse').path(master).path('teams').get();
  }

  public static listPipelinesForTeam(master: string, team: string): PromiseLike<string[]> {
    return API.path('concourse').path(master).path('teams').path(team).path('pipelines').get();
  }

  public static listJobsForPipeline(master: string, team: string, pipeline: string): PromiseLike<string[]> {
    return API.path('concourse')
      .path(master)
      .path('teams')
      .path(team)
      .path('pipelines')
      .path(pipeline)
      .path('jobs')
      .get();
  }

  public static listResourcesForPipeline(master: string, team: string, pipeline: string): PromiseLike<string[]> {
    return API.path('concourse')
      .path(master)
      .path('teams')
      .path(team)
      .path('pipelines')
      .path(pipeline)
      .path('resources')
      .get();
  }
}
