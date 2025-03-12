'use strict';

describe('Filter: regional', function () {
  beforeEach(window.module(require('./image.regional.filter').name));

  beforeEach(
    window.inject(function (_regionalFilter_) {
      this.regionalFilter = _regionalFilter_;
      this._ = _;

      this.images = [
        {
          name: 'null',
          region: null,
        },
        {
          name: 'west',
          region: 'west',
        },
        {
          name: 'east',
          region: 'east',
        },
      ];
    }),
  );

  it('filters the images based on the selected region and null', function () {
    var noEast = [
      {
        name: 'null',
        region: null,
      },
      {
        name: 'west',
        region: 'west',
      },
    ];

    expect(this.regionalFilter(this.images, 'west')).toEqual(noEast);
  });
});
