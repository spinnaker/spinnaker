'use strict';

describe('Filter: ipSort', function() {
  var filter;

  beforeEach(
    window.module(
      require('./ip.sort.filter.js')
    )
  );

  beforeEach(
    window.inject(
      function($filter) {
        filter = $filter('ipSorter');
      }
    )
  );

  it('sorts ip addresses, preserving original array', function() {
    var a = { address: '1.1.1.1' },
      b = { address: '1.1.1.0' },
      c = { address: '1.1.0.0' },
      d = { address: '1.0.1.0' },
      e = { address: '1.0.0.1' },
      f = { address: '1.0.0.0' },
      g = { address: '2.0.0.0' };

    var original = [a, b, c, d, e, f, g];

    var sorted = filter(original);

    expect(sorted).toEqual([f, e, d, c, b, a, g]);
    expect(original).toEqual([a, b, c, d, e, f, g]);

  });

});
