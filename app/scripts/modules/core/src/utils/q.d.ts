import { IQService, IPromise } from '@types/angular';

// add an overload for normalizing a regular Promise into an angular IPromise
declare module '@types/angular' {
  interface IQService {
    when<T>(promise: Promise<T>): IPromise<T>;
    resolve<T>(promise: Promise<T>): IPromise<T>;
  }
}
