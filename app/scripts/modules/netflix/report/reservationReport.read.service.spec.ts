import {IHttpBackendService, IPromise, mock} from 'angular';

import {SETTINGS} from 'core/config/settings';
import {
  IExtractedReservation,
  IReservation, IReservationReport,
  RESERVATION_REPORT_READER,
  ReservationReportReader
} from 'netflix/report/reservationReport.read.service';

describe('ReservationReport: ', () => {

  let $http: IHttpBackendService;
  let reader: ReservationReportReader;

  beforeEach(mock.module(RESERVATION_REPORT_READER));
  beforeEach(mock.inject(($httpBackend: IHttpBackendService,
                          reservationReportReader: ReservationReportReader) => {
    $http = $httpBackend;
    reader = reservationReportReader;
  }));

  describe('test reservation retrieval', () => {

    beforeEach(() => {
      $http.whenGET(`${SETTINGS.gateUrl}/reports/reservation/v2`).respond(200, []);
    });

    it('should return a promise', () => {
      const result: IPromise<IReservationReport> = reader.getReservations();
      $http.flush();
      expect(result.then).toBeDefined();
      expect(result.catch).toBeDefined();
    });
  });

  describe('test reservation extraction', () => {

    function getReservation(account: string, region: string, instanceType: string, id: string): IReservation {

      const reservation: IReservation = {
        accounts: {},
        availabilityZoneId: `azId_${id}`,
        instanceType,
        os: `os_${id}`,
        region,
        totalReserved: 1,
        totalSurplus: 2,
        totalUsed: 3
      };
      reservation.accounts[account] = {
        reserved: 1,
        surplus: 2,
        used: 3
      };

      return reservation;
    }

    it('should return an empty array if passed an empty array', () => {
      expect(reader.extractReservations([], 'account', 'region', 'instanceType').length).toBe(0);
    });

    it('should only extract the reservations whose regions, instanceTypes, and account match', () => {
      const accounts: string[] = ['test', 'prod'];
      const regions: string[] = ['us-west-1', 'us-west-2', 'us-east-1', 'eu-west-1'];
      const instanceTypes: string[] = ['small', 'medium', 'large'];

      const reservations: IReservation[] = [];
      accounts.forEach((account: string, i: number) => {
        regions.forEach((region: string, j: number) => {
          instanceTypes.forEach((instanceType: string, k: number) => {
            reservations.push(getReservation(account, region, instanceType, `${i}${j}${k}`));
          });
        });
      });

      const account = 'test';
      const region = 'us-west-2';
      const instanceType = 'small';
      const extracted: IExtractedReservation[] = reader.extractReservations(reservations, account, region, instanceType);
      expect(extracted.length).toBe(1);
      expect(extracted[0].os).toBe('os_010');
    });
  });
});
