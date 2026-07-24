import type { IFindImageParams, IFindTagsParams, IImage } from '@spinnaker/core';
import { REST, RetryService } from '@spinnaker/core';

export interface IDockerImage extends IImage {
  account: string;
  registry: string;
  repository: string;
  tag: string;
}

export class DockerImageReader {
  public static getImage(imageName: string, region: string, credentials: string): Promise<IDockerImage> {
    return REST('/images')
      .path(credentials, region, imageName)
      .query({ provider: 'docker' })
      .get()
      .then((results: IDockerImage[]) => (results && results.length ? results[0] : null))
      .catch((): IDockerImage => null);
  }

  public static findImages(params: IFindImageParams, signal?: AbortSignal): Promise<IDockerImage[]> {
    return RetryService.buildRetrySequence<IDockerImage[]>(
      () => REST('/images/find').query(params).get(),
      (results: IDockerImage[]) => results.length > 0,
      10,
      1000,
      signal,
    )
      .then((results: IDockerImage[]) => results)
      .catch((): IDockerImage[] => []);
  }

  public static findTags(params: IFindTagsParams, signal?: AbortSignal): Promise<string[]> {
    return RetryService.buildRetrySequence<string[]>(
      () => REST('/images/tags').query(params).get(),
      (results: string[]) => results.length > 0,
      10,
      1000,
      signal,
    )
      .then((results: string[]) => results)
      .catch((): string[] => []);
  }
}

export class DockerChartImageReader {
  public static getImage(imageName: string, region: string, credentials: string): Promise<IDockerImage> {
    return REST('/charts')
      .path(credentials, region, imageName)
      .query({ provider: 'docker' })
      .get()
      .then((results: IDockerImage[]) => (results && results.length ? results[0] : null))
      .catch((): IDockerImage => null);
  }

  public static findImages(params: IFindImageParams, signal?: AbortSignal): Promise<IDockerImage[]> {
    return RetryService.buildRetrySequence<IDockerImage[]>(
      () => REST('/charts/find').query(params).get(),
      (results: IDockerImage[]) => results.length > 0,
      10,
      1000,
      signal,
    )
      .then((results: IDockerImage[]) => results)
      .catch((): IDockerImage[] => []);
  }

  public static findTags(params: IFindTagsParams, signal?: AbortSignal): Promise<string[]> {
    return RetryService.buildRetrySequence<string[]>(
      () => REST('/charts/tags').query(params).get(),
      (results: string[]) => results.length > 0,
      10,
      1000,
      signal,
    )
      .then((results: string[]) => results)
      .catch((): string[] => []);
  }
}
