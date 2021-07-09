declare module 'js-worker-search' {
  type IndexMode = 'ALL_SUBSTRINGS' | 'EXACT_WORDS' | 'PREFIXES';

  class SearchApi {
    constructor(indexMode?: IndexMode, tokenizePattern?: RegExp, caseSensitive?: boolean);
    public indexDocument(uid: any, text: string): SearchApi;
    public search(query: string): Promise<any[]>;
  }

  export default SearchApi;
}
