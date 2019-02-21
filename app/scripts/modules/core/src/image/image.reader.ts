import { IPromise, module } from 'angular';

import { ProviderServiceDelegate, PROVIDER_SERVICE_DELEGATE } from 'core/cloudProvider/providerService.delegate';

export interface IFindImageParams {
  provider: string;
  q?: string;
  region?: string;
  account?: string;
  count?: number;
}

export interface IFindTagsParams {
  provider: string;
  account: string;
  repository: string;
}

// marker interface
export interface IImage {}

export interface IImageReader {
  findImages(params: IFindImageParams): IPromise<IImage[]>;
  getImage(imageName: string, region: string, credentials: string): IPromise<IImage>;
}

export class ImageReader {
  public static $inject = ['providerServiceDelegate'];
  public constructor(private providerServiceDelegate: ProviderServiceDelegate) {}

  private getDelegate(cloudProvider: string): IImageReader {
    return this.providerServiceDelegate.getDelegate<IImageReader>(cloudProvider, 'image.reader');
  }

  public findImages(params: IFindImageParams): IPromise<IImage[]> {
    return this.getDelegate(params.provider).findImages(params);
  }

  public getImage(cloudProvider: string, imageName: string, region: string, credentials: string): IPromise<IImage> {
    return this.getDelegate(cloudProvider).getImage(imageName, region, credentials);
  }
}

export const IMAGE_READER = 'spinnaker.core.image.reader';
module(IMAGE_READER, [PROVIDER_SERVICE_DELEGATE]).service('imageReader', ImageReader);
