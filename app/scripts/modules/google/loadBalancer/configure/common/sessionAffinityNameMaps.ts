import * as _ from 'lodash';

interface StringMap {
  [key: string]: string;
}

export const sessionAffinityViewToModelMap: StringMap = {
  'None': 'NONE',
  'Client IP': 'CLIENT_IP',
  'Generated Cookie': 'GENERATED_COOKIE',
  'Client IP and protocol': 'CLIENT_IP_PROTO',
  'Client IP, port and protocol': 'CLIENT_IP_PORT_PROTO',
};

export const sessionAffinityModelToViewMap = _.invert<StringMap, StringMap>(sessionAffinityViewToModelMap);
