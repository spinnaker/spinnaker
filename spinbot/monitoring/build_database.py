import logging
from .config import GetMonitoringConfig
from .influx_database import InfluxDatabase
from .noop_database import NoopDatabase

databases = {}

def FlushDatabaseWrites(align_points=False):
    for n, db in databases.items():
        db.write_all_points(align_points=align_points)

def GetDatabase(name):
    config = GetMonitoringConfig()
    result = databases.get(name, None)
    if result is None:
        influx = config.get('influx', None)
        if influx is not None:
            logging.info('Using influxdb')
            result = InfluxDatabase(influx, name)
        else:
            logging.warn('No monitoring database selected')
            result = NoopDatabase(name)
        databases[name] = result
    return result
