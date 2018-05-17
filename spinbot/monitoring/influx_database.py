from influxdb import InfluxDBClient
from .database import Database

class InfluxDatabase(Database):
    def __init__(self, props, database):
        super().__init__(database)

        host = props.get('host', 'localhost')
        port = props.get('port', 8086)
        self.client = InfluxDBClient(host=host, port=port)

        self.client.create_database(self.database)
        self.client.switch_database(self.database)

    def now(self):
        return super().now()

    def _write_all_points(self, points, align_points=False):
        if align_points:
            time = self.now()
            for p in points:
                p['time'] = time

        self.client.write_points(points)

    def _point(self, name, fields, tags={}):
        return {
            'measurement': name,
            'time': self.now(),
            'tags': tags,
            'fields': fields
        }

    def time_format(self):
        return '%Y-%m-%dT%H:%M:%SZ'
