var ApplicationFixture = {
  name: 'foo',
  attributes: {},
  clusters: {
    test:[
      {
        name: 'test',
        loadBalancers: [
          'foo-test-frontend',
        ],
        serverGroups: [
          'foo-test-v01',
        ]
      },
    ],
  },
};

