import { module } from 'angular';
import { PROVIDER_SERVICE_DELEGATE, ProviderServiceDelegate } from '../cloudProvider/providerService.delegate';

export type IFindImageParams = {
  provider: string;
  q?: string;
  region?: string;
  account?: string;
  count?: number;
};

export type IFindTagsParams = {
  provider: string;
  account: string;
  repository: string;
};

// marker interface
export interface IImage {}

export interface IImageReader {
  findImages(params: IFindImageParams): PromiseLike<IImage[]>;
  getImage(imageName: string, region: string, credentials: string): PromiseLike<IImage>;
}

export class ImageReader {
  public static $inject = ['providerServiceDelegate'];
  public constructor(private providerServiceDelegate: ProviderServiceDelegate) {}

  private getDelegate(cloudProvider: string): IImageReader {
    return this.providerServiceDelegate.getDelegate<IImageReader>(cloudProvider, 'image.reader');
  }

  public findImages(params: IFindImageParams): PromiseLike<IImage[]> {
    return this.getDelegate(params.provider).findImages(params);
  }

  public getImage(cloudProvider: string, imageName: string, region: string, credentials: string): PromiseLike<IImage> {
    return this.getDelegate(cloudProvider).getImage(imageName, region, credentials);
  }
}

export const IMAGE_READER = 'spinnaker.core.image.reader';
module(IMAGE_READER, [PROVIDER_SERVICE_DELEGATE]).service('imageReader', ImageReader);
