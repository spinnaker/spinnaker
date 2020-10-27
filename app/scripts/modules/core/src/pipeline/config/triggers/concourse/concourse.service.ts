

import { API } from 'core/api/ApiService';

export class ConcourseService {
  public static listTeamsForMaster(master: string): PromiseLike<string[]> {
    return API.one('concourse').one(master).one('teams').get();
  }

  public static listPipelinesForTeam(master: string, team: string): PromiseLike<string[]> {
    return API.one('concourse').one(master).one('teams').one(team).one('pipelines').get();
  }

  public static listJobsForPipeline(master: string, team: string, pipeline: string): PromiseLike<string[]> {
    return API.one('concourse').one(master).one('teams').one(team).one('pipelines').one(pipeline).one('jobs').get();
  }

  public static listResourcesForPipeline(master: string, team: string, pipeline: string): PromiseLike<string[]> {
    return API.one('concourse')
      .one(master)
      .one('teams')
      .one(team)
      .one('pipelines')
      .one(pipeline)
      .one('resources')
      .get();
  }
}
