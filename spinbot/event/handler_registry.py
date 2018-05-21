import importlib
import logging
from os import listdir
from os.path import isfile, join, dirname, realpath

handlers = []
conf = {}

def ConfigureHandlers(_conf):
    conf.update(_conf)
    handlers = _conf.get('handlers')
    if not handlers:
        logging.warn('No handlers registered')
        return

    dir_path = dirname(realpath(__file__))
    for h in handlers:
        name = h.get('name')
        f = '{}.py'.format(name)
        if isfile(join(dir_path, f)):
            logging.info('Registering {}'.format(h))
            importlib.import_module('event.{}'.format(name))
        else:
            logging.warn('{} is not a valid handler name, ignoring it.'.format(f))

def GetConfig():
    return conf

def GetHandlerConfig(name):
    handler = next((h for h in conf.get('handlers', []) if h.get('name') == name), {})
    return handler.get('config', {})

def RegisterHandler(handler):
    if len([h for h in handlers if h.id == handler.id]) > 0:
        raise RuntimeError("Duplicate handler registered: {}".format(handler.id))

    handlers.append(handler)

def Handlers():
    return handlers
