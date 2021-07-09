import { isNameInUse, isNameLong, isValidHealthCheckInterval, isValidTimeout } from './targetGroupValidators';

const mockTargetGroup = {
  attributes: {
    deregistrationDelay: 300,
    stickinessDuration: 8400,
    stickinessEnabled: false,
    stickinessType: 'lb_cookie',
  },
  healthCheckInterval: 10,
  healthCheckPath: '/healthcheck',
  healthCheckPort: 7001,
  healthCheckProtocol: 'HTTP',
  healthCheckTimeout: 5,
  healthyThreshold: 10,
  name: 'targetgroup',
  port: 7001,
  protocol: 'HTTP',
  targetType: 'ip',
  unhealthyThreshold: 2,
  account: 'test',
  cloudProvider: 'aws',
  healthTimeout: 1000,
  healthInterval: 10,
  loadBalancerNames: ['loadbalancer1', 'loadbalancer2'],
  region: 'us-east-1',
  type: '',
};

const httpTargetGroup = {
  ...mockTargetGroup,
  protocol: 'TCP',
  healthCheckProtocol: 'HTTP',
};

const httpsTargetGroup = {
  ...mockTargetGroup,
  protocol: 'TCP',
  healthCheckProtocol: 'HTTPS',
};

const tcpTargetGroup = {
  ...mockTargetGroup,
  healthCheckProtocol: 'TCP',
};

describe('Target Group validators', () => {
  describe('of used names', () => {
    it('returns an error if the name exists already', () => {
      const existingGroups = {
        test: {
          'us-east-1': [mockTargetGroup.name],
        },
      };
      const actual = isNameInUse(existingGroups, 'test', 'us-east-1')(mockTargetGroup.name);
      expect(actual).toBeTruthy();
    });

    it('returns an null if it does not exist', () => {
      const existingGroups = {
        test: {
          'us-east-1': ['targetgroup2'],
        },
      };
      const actual = isNameInUse(existingGroups, 'test', 'us-east-1')(mockTargetGroup.name);
      expect(actual).toEqual(null);
    });
  });

  describe('of name length', () => {
    it('returns an error if the inputted name >32 additional characters (this is prepended to the application name)', () => {
      const actual = isNameLong('application'.length)(`applicationwithareallylongname`);
      expect(actual).toBeTruthy();
    });

    it('returns null if the name is < 32', () => {
      const actual = isNameLong('app'.length)('appwithshortname');
      expect(actual).toEqual(null);
    });
  });

  describe('of health check timeout constraints', () => {
    it('should have a 6s timeout', () => {
      const actual = isValidTimeout(httpTargetGroup)('8');
      expect(actual).toBeTruthy();
    });

    it('should be 6s and is valid', () => {
      const actual = isValidTimeout(httpTargetGroup)('6');
      expect(actual).toEqual(null);
    });

    it('should have a 10s timeout', () => {
      const actual = isValidTimeout(httpsTargetGroup)('9');
      expect(actual).toBeTruthy();
    });

    it('should be 10s and is valid', () => {
      const actual = isValidTimeout(httpsTargetGroup)('10');
      expect(actual).toEqual(null);
    });

    it('should not have a timeout constraint', () => {
      const actual = isValidTimeout(mockTargetGroup)('10');
      expect(actual).toEqual(null);
    });
  });

  describe('of health check interval', () => {
    it('TCPs can have a 10s interval', () => {
      const actual = isValidHealthCheckInterval(tcpTargetGroup)('10');
      expect(actual).toEqual(null);
    });

    it('TCPs can have a 30s interval', () => {
      const actual = isValidHealthCheckInterval(tcpTargetGroup)('30');
      expect(actual).toEqual(null);
    });

    it('returns an error when it fails TCP rules', () => {
      const actual = isValidHealthCheckInterval(tcpTargetGroup)('20');
      expect(actual).toBeTruthy();
    });

    it('is not a TCP protocol target group', () => {
      const actual = isValidHealthCheckInterval(mockTargetGroup)('20');
      expect(actual).toEqual(null);
    });
  });
});
