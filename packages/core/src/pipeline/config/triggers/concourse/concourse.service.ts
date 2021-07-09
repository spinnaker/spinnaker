import { REST } from '../../../../api/ApiService';

export class ConcourseService {
  public static listTeamsForMaster(master: string): PromiseLike<string[]> {
    return REST('/concourse').path(master, 'teams').get();
  }

  public static listPipelinesForTeam(master: string, team: string): PromiseLike<string[]> {
    return REST('/concourse').path(master, 'teams', team, 'pipelines').get();
  }

  public static listJobsForPipeline(master: string, team: string, pipeline: string): PromiseLike<string[]> {
    return REST('/concourse').path(master, 'teams', team, 'pipelines', pipeline, 'jobs').get();
  }

  public static listResourcesForPipeline(master: string, team: string, pipeline: string): PromiseLike<string[]> {
    return REST('/concourse').path(master, 'teams', team, 'pipelines', pipeline, 'resources').get();
  }
}
