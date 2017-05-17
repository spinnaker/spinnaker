'use strict';

describe('Filter: instanceList', function () {

  var filter;

  beforeEach(
    window.module(
      require('./instanceList.filter.js')
    )
  );

  beforeEach(
    window.inject(
      function($filter) {
        filter = $filter('instanceSearch');
      }
    )
  );

  it('should be instantiated', function () {
    expect(filter).toBeDefined();
  });

  it('should not filter instances if the query is "i-"', function () {
    var instanceList = [{id:'i-abcd'}];
    var query = 'i-';
    expect(filter(instanceList, query )).toEqual(instanceList);
  });

  it('should not filter instances if the query starts with "i-" and contains a partial of the id', function () {
    var instanceList = [{id:'i-abcd'}];
    var query = 'i-ab';
    expect(filter(instanceList, query )).toEqual(instanceList);
  });

  it('should not filter instances if the query does not starts with "i-"', function () {
    var instanceList = [{id:'i-abcd'}];
    var query = 'v078';
    expect(filter(instanceList, query )).toEqual(instanceList);
  });

  it('should filter instances if the query starts with "i-" and DOES NOT contains a partial of the id', function () {
    var instanceList = [{id:'i-abcd'}];
    var query = 'i-foo';
    expect(filter(instanceList, query )).toEqual([]);
  });

  it('should not filter on "i-" if it is not in the beginning', function () {
    var instanceList = [{id:'i-1234'}];
    var query = 'api-int';
    expect(filter(instanceList, query )).toEqual(instanceList);
  });
});


