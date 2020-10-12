/**
 * Based 100% on Angular internals
 */

export type QueryParamVal = string | boolean | any[];

export interface IQueryParams {
  [key: string]: QueryParamVal;
}

export class UrlParser {
  /**
   * Parses an escaped url query string into key-value pairs.
   * @returns {Object.<string,boolean|Array>}
   */
  public static parseQueryString(query = ''): IQueryParams {
    const result = {} as IQueryParams;
    query.split('&').forEach((keyValue) => {
      let splitPoint, key, val;
      if (keyValue) {
        key = keyValue = keyValue.replace(/\+/g, '%20');
        splitPoint = keyValue.indexOf('=');
        if (splitPoint !== -1) {
          key = keyValue.substring(0, splitPoint);
          val = keyValue.substring(splitPoint + 1);
        }
        key = this.tryDecodeURIComponent(key);
        if (key !== null) {
          val = val && val.length > 0 ? this.tryDecodeURIComponent(val) : true;
          if (result[key] === undefined) {
            result[key] = val;
          } else if (Array.isArray(result[key])) {
            (result[key] as any[]).push(val);
          } else {
            result[key] = [result[key], val];
          }
        }
      }
    });
    return result;
  }

  private static tryDecodeURIComponent(value: string): string {
    try {
      return decodeURIComponent(value);
    } catch (e) {
      return null;
    }
  }
}
