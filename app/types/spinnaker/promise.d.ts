/* eslint-disable @spinnaker/prefer-promise-like */

import { IPromise } from '@types/angular';

// add an overload for normalizing a regular Promise into an angular IPromise
declare module '@types/angular' {
  interface IPromise<T> {
    then<TResult>(
      successCallback: (promiseValue: T) => PromiseLike<TResult> | TResult,
      errorCallback?: null | undefined,
      notifyCallback?: (state: any) => any,
    ): IPromise<TResult>;
    then<TResult1, TResult2>(
      successCallback: (promiseValue: T) => PromiseLike<TResult1> | TResult2,
      errorCallback?: null | undefined,
      notifyCallback?: (state: any) => any,
    ): IPromise<TResult1 | TResult2>;

    then<TResult, TCatch>(
      successCallback: (promiseValue: T) => PromiseLike<TResult> | TResult,
      errorCallback: (reason: any) => PromiseLike<TCatch> | TCatch,
      notifyCallback?: (state: any) => any,
    ): IPromise<TResult | TCatch>;
    then<TResult1, TResult2, TCatch1, TCatch2>(
      successCallback: (promiseValue: T) => PromiseLike<TResult1> | TResult2,
      errorCallback: (reason: any) => PromiseLike<TCatch1> | TCatch2,
      notifyCallback?: (state: any) => any,
    ): IPromise<TResult1 | TResult2 | TCatch1 | TCatch2>;
  }

  interface IQService {
    /** Allows $q.all([p1, p2, p3]) to be called with PromiseLike */
    all<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10>(
      values: [
        T1 | PromiseLike<T1>,
        T2 | PromiseLike<T2>,
        T3 | PromiseLike<T3>,
        T4 | PromiseLike<T4>,
        T5 | PromiseLike<T5>,
        T6 | PromiseLike<T6>,
        T7 | PromiseLike<T7>,
        T8 | PromiseLike<T8>,
        T9 | PromiseLike<T9>,
        T10 | PromiseLike<T10>,
      ],
    ): IPromise<[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10]>;
    all<T1, T2, T3, T4, T5, T6, T7, T8, T9>(
      values: [
        T1 | PromiseLike<T1>,
        T2 | PromiseLike<T2>,
        T3 | PromiseLike<T3>,
        T4 | PromiseLike<T4>,
        T5 | PromiseLike<T5>,
        T6 | PromiseLike<T6>,
        T7 | PromiseLike<T7>,
        T8 | PromiseLike<T8>,
        T9 | PromiseLike<T9>,
      ],
    ): IPromise<[T1, T2, T3, T4, T5, T6, T7, T8, T9]>;
    all<T1, T2, T3, T4, T5, T6, T7, T8>(
      values: [
        T1 | PromiseLike<T1>,
        T2 | PromiseLike<T2>,
        T3 | PromiseLike<T3>,
        T4 | PromiseLike<T4>,
        T5 | PromiseLike<T5>,
        T6 | PromiseLike<T6>,
        T7 | PromiseLike<T7>,
        T8 | PromiseLike<T8>,
      ],
    ): IPromise<[T1, T2, T3, T4, T5, T6, T7, T8]>;
    all<T1, T2, T3, T4, T5, T6, T7>(
      values: [
        T1 | PromiseLike<T1>,
        T2 | PromiseLike<T2>,
        T3 | PromiseLike<T3>,
        T4 | PromiseLike<T4>,
        T5 | PromiseLike<T5>,
        T6 | PromiseLike<T6>,
        T7 | PromiseLike<T7>,
      ],
    ): IPromise<[T1, T2, T3, T4, T5, T6, T7]>;
    all<T1, T2, T3, T4, T5, T6>(
      values: [
        T1 | PromiseLike<T1>,
        T2 | PromiseLike<T2>,
        T3 | PromiseLike<T3>,
        T4 | PromiseLike<T4>,
        T5 | PromiseLike<T5>,
        T6 | PromiseLike<T6>,
      ],
    ): IPromise<[T1, T2, T3, T4, T5, T6]>;
    all<T1, T2, T3, T4, T5>(
      values: [
        T1 | PromiseLike<T1>,
        T2 | PromiseLike<T2>,
        T3 | PromiseLike<T3>,
        T4 | PromiseLike<T4>,
        T5 | PromiseLike<T5>,
      ],
    ): IPromise<[T1, T2, T3, T4, T5]>;
    all<T1, T2, T3, T4>(
      values: [T1 | PromiseLike<T1>, T2 | PromiseLike<T2>, T3 | PromiseLike<T3>, T4 | PromiseLike<T4>],
    ): IPromise<[T1, T2, T3, T4]>;
    all<T1, T2, T3>(values: [T1 | PromiseLike<T1>, T2 | PromiseLike<T2>, T3 | PromiseLike<T3>]): IPromise<[T1, T2, T3]>;
    all<T1, T2>(values: [T1 | PromiseLike<T1>, T2 | PromiseLike<T2>]): IPromise<[T1, T2]>;
    all<TAll>(promises: Array<PromiseLike<TAll>>): IPromise<TAll[]>;
    /** Allows $q.when(p1) to be called with PromiseLike */
    when<T>(promise: PromiseLike<T>): IPromise<T>;
    /** Allows $q.resolve(p1) to be called with PromiseLike */
    resolve<T>(promise: PromiseLike<T>): IPromise<T>;
  }
}

declare global {
  // This allows an angular $q promise to be returned as a PromiseLike<T>
  interface PromiseLike<T> {
    // Copied from lib.es5.d.ts
    /**
     * Attaches callbacks for the resolution and/or rejection of the Promise.
     * @param onfulfilled The callback to execute when the Promise is resolved.
     * @param onrejected The callback to execute when the Promise is rejected.
     * @returns A Promise for the completion of which ever callback is executed.
     */
    then<TResult1 = T, TResult2 = never>(
      onfulfilled?: ((value: T) => TResult1 | PromiseLike<TResult1>) | undefined | null,
      onrejected?: ((reason: any) => TResult2 | PromiseLike<TResult2>) | undefined | null,
    ): PromiseLike<TResult1 | TResult2>;

    /**
     * Attaches a callback for only the rejection of the Promise.
     * @param onrejected The callback to execute when the Promise is rejected.
     * @returns A Promise for the completion of the callback.
     */
    catch<TResult = never>(
      onrejected?: ((reason: any) => TResult | PromiseLike<TResult>) | undefined | null,
    ): PromiseLike<T | TResult>;

    // copied from lib.es2018.promise.d.ts
    /**
     * Attaches a callback that is invoked when the Promise is settled (fulfilled or rejected). The
     * resolved value cannot be modified from the callback.
     * @param onfinally The callback to execute when the Promise is settled (fulfilled or rejected).
     * @returns A Promise for the completion of the callback.
     */
    finally(onfinally?: (() => void) | undefined | null): PromiseLike<T>;
  }
}
