import sys, json, os, uuid, base64
from io import BytesIO

from urllib.parse import urlparse

from oci.config import from_file, validate_config
from oci.regions import endpoint_for
from oci.object_storage.object_storage_client import ObjectStorageClient
from oci.object_storage.models import CreateBucketDetails, CreatePreauthenticatedRequestDetails
from oci.exceptions import ServiceError

from datetime import timedelta, datetime
import humanfriendly
import rfc3339

from jinja2 import Environment, FileSystemLoader, select_autoescape

import qrcode

def main():
    config = from_file(file_location="/code/oci/config")
    validate_config(config)
    object_storage = ObjectStorageClient(config)

    env = Environment(
        loader=FileSystemLoader("/code/templates"),
        autoescape=select_autoescape(["html", "xml"]))

    data = parse_input(object_storage)

    create_bucket(object_storage, data)
    vote_par = create_vote_par(object_storage, data)

    create_ballot_json(object_storage, data)
    ballot_json_par = create_ballot_par(object_storage, "ballot.json", data)

    create_ballot_html(object_storage, env, data, vote_par, ballot_json_par)
    ballot_html_par = create_ballot_par(object_storage, "ballot.html", data)

    create_result_html(object_storage, env, data, ballot_html_par)

    print("Ballot: {0}{1}".format(data["oci_url"], ballot_html_par))
    print("Results: {0}".format(data["result_url"]))

def parse_input(object_storage):
    data  = json.load(sys.stdin)
    poll_id = uuid.uuid4()

    poll_duration = humanfriendly.parse_timespan(data["for"] if "for" in data else "1d")
    poll_expiry = datetime.now() + timedelta(seconds=poll_duration)

    data = {
        "poll_name": data["name"],
        "poll_id": str(poll_id),
        "bucket_name": "{1}".format(data["name"], poll_id),
        "poll_options": data["options"] if "options" in data else ["Yes", "No"],
        "poll_expiry": rfc3339.format(poll_expiry, utc=True, use_system_timezone=False),
        "poll_description": data["description"] if "description" in data else "",
        "oci_url": os.environ["OCI_REGION_BASE_URL"],
        "compartment_id": os.environ["COMPARTMENT_ID"],
        "results_bucket": os.environ["RESULTS_BUCKET"],
        "namespace_name": object_storage.get_namespace().data,
        "vote_url": os.environ["FN_REQUEST_URL"].replace("/new", "/vote"),
        "close_url": os.environ["FN_REQUEST_URL"].replace("/new", "/close")
    }

    data["result_url"] = "{0}/n/{1}/b/{2}/o/{3}.html".format(
        data["oci_url"], data["namespace_name"],
        data["results_bucket"], data["poll_id"])

    data["qrcode"] = create_qrcode(data)

    return data

def create_bucket(object_storage, data):
    create_bucket_details = CreateBucketDetails(
        name=data["bucket_name"],
        compartment_id=data["compartment_id"],
        public_access_type="NoPublicAccess",
        storage_tier="Standard")

    object_storage.create_bucket(data["namespace_name"], create_bucket_details)

def create_vote_par(object_storage, data):
    vote_par_details = CreatePreauthenticatedRequestDetails(
        name="Vote {0}".format(data["bucket_name"]),
        access_type="AnyObjectWrite",
        time_expires=data["poll_expiry"])

    return object_storage.create_preauthenticated_request(
        data["namespace_name"], data["bucket_name"], vote_par_details
    ).data.access_uri

def create_ballot_html(object_storage, env, data, vote_par, json_par):
    ballot_template = env.get_template("ballot.html")
    ballot_html = ballot_template.render(
        options=data["poll_options"],
        expiry=data["poll_expiry"],
        description=data["poll_description"],
        name=data["poll_name"],
        vote_url=data["vote_url"],
        result_url=data["result_url"],
        qrcode=data["qrcode"],
        poll_id=data["poll_id"],
        close_url=data["close_url"],
        vote_par="{0}{1}".format(data["oci_url"], vote_par),
        json_par="{0}{1}".format(data["oci_url"], json_par))

    object_storage.put_object(
        data["namespace_name"], data["bucket_name"],
        "ballot.html", ballot_html,
        content_type="text/html")

def create_ballot_json(object_storage, data):
    object_storage.put_object(
        data["namespace_name"], data["bucket_name"],
        "ballot.json", json.dumps(data))

def create_ballot_par(object_storage, object_name, data):
    ballot_par_details = CreatePreauthenticatedRequestDetails(
        name="Ballot {0} {1}".format(object_name, data["bucket_name"]),
        object_name=object_name,
        access_type="ObjectRead",
        time_expires=data["poll_expiry"])

    return object_storage.create_preauthenticated_request(
        data["namespace_name"], data["bucket_name"], ballot_par_details
    ).data.access_uri

def create_result_html(object_storage, env, data, ballot_par):
    result_template = env.get_template("result_open.html")
    result_html = result_template.render(
        poll_id=data["poll_id"],
        ballot_url="{0}{1}".format(data["oci_url"], ballot_par))

    object_storage.put_object(
        data["namespace_name"], data["results_bucket"],
        "{0}.html".format(data["poll_id"]), result_html,
        content_type="text/html")

def create_qrcode(data):
    qr = qrcode.QRCode(
        version=None,
        error_correction=qrcode.constants.ERROR_CORRECT_H,
        box_size=20,
        border=4,
    )
    qr.add_data(data["result_url"])
    qr.make(fit=True)
    img = qr.make_image()
    qr_bytes = BytesIO()
    img.save(qr_bytes, "PNG")
    contents = qr_bytes.getvalue()
    qr_bytes.close()

    return "data:image/png;base64,{0}".format(base64.b64encode(contents).decode())


def eprint(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)

if __name__ == "__main__":
    main()
