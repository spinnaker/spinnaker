import logging
import traceback
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
    try:
        if result is None:
            influx = config.get('influx', None)
            if influx is not None:
                logging.info('Using influxdb')
                result = InfluxDatabase(influx, name)
    except Exception as e:
        logging.error(traceback.format_exc())
        logging.error('Failed to load database')

    if result is None:
        logging.warn('Defaulting to noop database')
        result = NoopDatabase(name)

    databases[name] = result
    return result
