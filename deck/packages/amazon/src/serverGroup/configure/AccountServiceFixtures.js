const AccountServiceFixture = {};
AccountServiceFixture.credentialsKeyedByAccount = {
  test: {
    regions: [
      {
        name: 'us-east-1',
        availabilityZones: ['a', 'b', 'c'],
      },
      {
        name: 'us-west-1',
        availabilityZones: ['b', 'c', 'd'],
      },
    ],
  },
  prod: {
    regions: [
      {
        name: 'us-east-1',
        availabilityZones: ['a', 'b', 'c', 'd', 'e'],
      },
      {
        name: 'us-west-1',
        availabilityZones: ['a', 'b', 'c', 'g', 'h', 'i'],
      },
    ],
  },
};

AccountServiceFixture.preferredZonesByAccount = {
  test: {
    'us-east-1': ['a', 'b', 'c'],
    'us-west-2': ['a', 'b', 'c'],
    'us-west-1': ['c', 'd'],
  },
  prod: {
    'us-east-1': ['d', 'e'],
    'us-west-1': ['g', 'h', 'i'],
  },
  default: {
    'us-east-1': ['a', 'b', 'c'],
    'us-west-1': ['c', 'd'],
  },
};

module.exports = AccountServiceFixture;
