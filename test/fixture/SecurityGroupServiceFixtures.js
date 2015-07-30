var securityGroupReaderFixture = {};
securityGroupReaderFixture.allSecurityGroups = {
  'test': {
    aws: {
      'us-east-1': [],
      'us-west-1': []
    }
  },
  'prod': {
    aws: {
      'us-east-1': [],
      'us-west-1': []
    }
  }
};

module.exports = securityGroupReaderFixture;
