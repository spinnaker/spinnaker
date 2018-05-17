from .local_storage import LocalStorage
from .gcs_storage import GcsStorage

def BuildStorage(props):
    local = props.get('local', None)
    gcs = props.get('gcs', None)
    if local is not None:
        return LocalStorage(local.get('path'))
    elif gcs is not None:
        return GcsStorage(gcs.get('bucket'), 
                gcs.get('path'), 
                project=gcs.get('project'), 
                json_path=gcs.get('json_path'))
    else:
        return LocalStorage(None)
