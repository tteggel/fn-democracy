import requests
import os, sys, uuid
from urllib.parse import urlparse, parse_qs
from jinja2 import Environment, FileSystemLoader, select_autoescape

def main():
    request_url = urlparse(os.environ['FN_REQUEST_URL'])
    q = parse_qs(request_url.query)
    vote_par = q['v'][0]
    json_par = q['j'][0]
    option = q['o'][0]

    eprint(vote_par)
    eprint(json_par)
    eprint(option)

    vote_data = requests.get(json_par).json()
    eprint(vote_data)

    vote_url = "{0}{1}".format(vote_par, uuid.uuid4())
    requests.put(vote_url, data=option)
    requests.get(vote_url)

    print(get_html())

def get_html():
    env = Environment(
        loader=FileSystemLoader("/code/templates"),
        autoescape=select_autoescape(["html", "xml"]))

    vote_template = env.get_template("vote.html")

    return vote_template.render()

def eprint(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)

if __name__ == "__main__":
    main()
