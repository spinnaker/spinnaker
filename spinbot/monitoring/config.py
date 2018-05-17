conf = {}

def ConfigureMonitoring(_conf):
    if _conf is not None:
        conf.update(_conf)

def GetMonitoringConfig():
    return conf
