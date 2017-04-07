import {module} from 'angular';

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
  findImages(params: IFindImageParams): ng.IPromise<IImage[]>;
  getImage(imageName: string, region: string, credentials: string): ng.IPromise<IImage>;
}

export class ImageReader {

  static get $inject() { return ['serviceDelegate']; }

  public constructor(private serviceDelegate: any) {}

  private getDelegate(cloudProvider: string): IImageReader {
    return this.serviceDelegate.getDelegate(cloudProvider, 'image.reader');
  }

  public findImages(params: IFindImageParams): ng.IPromise<IImage[]> {
    return this.getDelegate(params.provider).findImages(params);
  }

  public getImage(cloudProvider: string, imageName: string, region: string, credentials: string): ng.IPromise<IImage> {
    return this.getDelegate(cloudProvider).getImage(imageName, region, credentials);
  }
}

export const IMAGE_READER = 'spinnaker.core.image.reader';
module(IMAGE_READER, [
  require('core/cloudProvider/serviceDelegate.service')
]).service('imageReader', ImageReader);
