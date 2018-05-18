import importlib
import logging
from os import listdir
from os.path import isfile, join, dirname, realpath

policies = []
conf = {}

def ConfigurePolicies(_conf):
    conf.update(_conf)
    policies = _conf.get('policies')
    if policies is None:
        logging.warn('{} no policies registered')
        return

    dir_path = dirname(realpath(__file__))
    for p in policies:
        name = p.get('name')
        f = '{}.py'.format(name)
        if isfile(join(dir_path, f)):
            logging.info('Registering {}'.format(p))
            importlib.import_module('policy.{}'.format(name))
        else:
            logging.warn('{} is not a valid policy name, ignoring it.'.format(f))

def GetPolicyConfig(name):
    policy = next((p for p in conf.get('policies', []) if p.get('name') == name), {})
    return policy.get('config', {})

def GetConfig():
    return conf

def RegisterPolicy(policy):
    if len([p for p in policies if p.id == policy.id]) > 0:
        raise RuntimeError("Duplicate policy registered: {}".format(policy.id))

    policies.append(policy)

def Policies():
    return policies
