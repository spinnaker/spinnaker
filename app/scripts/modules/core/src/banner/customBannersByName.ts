import { Application } from '../application';
/**
 * Import all custom banner components here.
 * The exported component's key should correlate to the banner key in the config from IBannerSettings.
 *
 * Ex:
 *
 * // SETTINGS
 * banners: [
 *  { key: 'myCoolBanner', active: true, routes: ["clusters"]}
 * ]
 *
 * // This file
 * import { MyBannerComp } from '../customBannerFolder/banner';
 * export const customBannersByName = { myCoolBanner: MyBannerComp }
 */

export interface IBannerProps {
  app: Application;
}
export interface ICustomBannersByName {
  [key: string]: ({ app }: IBannerProps) => JSX.Element;
}

export const customBannersByName = {} as ICustomBannersByName;
