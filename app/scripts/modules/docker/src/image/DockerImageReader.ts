import { IPromise } from 'angular';

import { API, IFindImageParams, IFindTagsParams, IImage, RetryService } from '@spinnaker/core';

export interface IDockerImage extends IImage {
  account: string;
  registry: string;
  repository: string;
  tag: string;
}

export class DockerImageReader {
  public static getImage(imageName: string, region: string, credentials: string): IPromise<IDockerImage> {
    return API.all('images')
      .one(credentials)
      .one(region)
      .one(imageName)
      .withParams({ provider: 'docker' })
      .get()
      .then((results: IDockerImage[]) => (results && results.length ? results[0] : null))
      .catch((): IDockerImage => null);
  }

  public static findImages(params: IFindImageParams): IPromise<IDockerImage[]> {
    return RetryService.buildRetrySequence<IDockerImage[]>(
      () => API.all('images/find').getList(params),
      (results: IDockerImage[]) => results.length > 0,
      10,
      1000,
    )
      .then((results: IDockerImage[]) => results)
      .catch((): IDockerImage[] => []);
  }

  public static findTags(params: IFindTagsParams): IPromise<string[]> {
    return RetryService.buildRetrySequence<string[]>(
      () => API.all('images/tags').getList(params),
      (results: string[]) => results.length > 0,
      10,
      1000,
    )
      .then((results: string[]) => results)
      .catch((): string[] => []);
  }
}
