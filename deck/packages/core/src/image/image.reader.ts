import type { ProviderServiceDelegate } from '../cloudProvider/providerService.delegate';

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
  public constructor(private providerServiceDelegate: ProviderServiceDelegate) {}

  private getDelegate(cloudProvider: string): IImageReader {
    return this.providerServiceDelegate.getDelegate<IImageReader>(cloudProvider, 'image.reader');
  }

  public findImages(params: IFindImageParams): Promise<IImage[]> {
    return Promise.resolve(this.getDelegate(params.provider).findImages(params));
  }

  public getImage(cloudProvider: string, imageName: string, region: string, credentials: string): Promise<IImage> {
    return Promise.resolve(this.getDelegate(cloudProvider).getImage(imageName, region, credentials));
  }
}
