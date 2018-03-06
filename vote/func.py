import requests
import os, sys, uuid
from urllib.parse import urlparse, parse_qs
from jinja2 import Environment, FileSystemLoader, select_autoescape

def main():
    try:
        request_url = urlparse(os.environ['FN_REQUEST_URL'])
        q = parse_qs(request_url.query)
        vote_par = q['v'][0]
        poll_id = q['i'][0]
        option = q['o'][0]

        eprint(vote_par)
        eprint(poll_id)
        eprint(option)

        vote_url = "{0}{1}".format(vote_par, uuid.uuid4())
        response = requests.put(vote_url, data=option)
        if(response.status_code != 200):
            raise Exception("Vote file put failed.")

        print(get_html())
    except Exception as e:
        print(get_error_html())

def get_html():
    env = Environment(
        loader=FileSystemLoader("/code/templates"),
        autoescape=select_autoescape(["html", "xml"]))

    vote_template = env.get_template("vote.html")

    return vote_template.render()

def get_error_html():
    env = Environment(
        loader=FileSystemLoader("/code/templates"),
        autoescape=select_autoescape(["html", "xml"]))

    vote_template = env.get_template("error.html")

    return vote_template.render()

def eprint(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)

if __name__ == "__main__":
    main()
