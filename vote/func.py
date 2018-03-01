import requests
import os, sys, uuid
from urllib.parse import urlparse, parse_qs

def eprint(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)

request_url = urlparse(os.environ['FN_REQUEST_URL'])
q = parse_qs(request_url.query)
par = q['r'][0]
option = q['o'][0]

eprint(par)
eprint(option)

requests.put("{0}{1}".format(par, uuid.uuid4()), data=option)
