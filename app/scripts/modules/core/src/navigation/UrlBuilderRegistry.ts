import { IUrlBuilder } from './UrlBuilder';

export class UrlBuilderRegistry {
  private builders: { [key: string]: IUrlBuilder } = {};

  public register(key: string, builder: IUrlBuilder) {
    this.builders[key] = builder;
  }

  public getBuilder(key: string): IUrlBuilder {
    return this.builders[key];
  }
}
