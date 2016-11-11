import {module} from 'angular';
import {API_SERVICE, Api} from 'core/api/api.service';
import {RETRY_SERVICE, RetryService} from 'core/retry/retry.service';

export interface IDockerImage {
  account: string;
  registry: string;
  repository: string;
  tag: string;
}

export class DockerImageReaderService {

  constructor(private API: Api,
              private retryService: RetryService) {}

  public findImages(params: any): ng.IPromise<IDockerImage[]> {
    return this.retryService.buildRetrySequence(() => this.API.all('images/find')
      .getList(params), (results: any) => (results.length > 0), 10, 1000)
      .then((results: any) => results)
      .catch((): any => []);
  }
}

export const DOCKER_IMAGE_READER_SERVICE = 'spinnaker.docker.image.reader';
module(DOCKER_IMAGE_READER_SERVICE, [API_SERVICE, RETRY_SERVICE])
  .service('dockerImageReader', DockerImageReaderService);
