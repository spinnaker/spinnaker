import os
import yaml
from .storage import Storage

class LocalStorage(Storage):
    def __init__(self, path):
        if path is None:
            path = '~/.spinbot/cache'
        self.path = os.path.expanduser(path)

        super().__init__()

    def store(self, key, val):
        with open(self.path, 'w+') as f:
            props = yaml.safe_load(f)
            if props is None:
                props = {}
            props[key] = val
            f.write(yaml.safe_dump(props))

    def load(self, key):
        try:
            with open(self.path, 'r') as f:
                props = yaml.safe_load(f)
                if props is None:
                    props = {}
                return props.get(key, None)
        except FileNotFoundError:
            return None
