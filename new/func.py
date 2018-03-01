import sys, json, os

from oci.config import from_file, validate_config
from oci.regions import endpoint_for
from oci.object_storage.object_storage_client import ObjectStorageClient
from oci.object_storage.models import CreateBucketDetails, CreatePreauthenticatedRequestDetails
from oci.exceptions import ServiceError

from datetime import timedelta, datetime
import rfc3339

from jinja2 import Environment, FileSystemLoader, select_autoescape

def main():
    config = from_file(file_location="/code/oci/config")
    validate_config(config)
    object_storage = ObjectStorageClient(config)

    data = parse_input(sys.stdin)
    data["namespace_name"] = object_storage.get_namespace().data

    create_bucket(object_storage, data)
    vote_par = create_vote_par(object_storage, data)
    create_ballot_html(object_storage, data, vote_par)
    ballot_par = create_ballot_par(object_storage, data)

    print("{0}{1}".format(data["oci_url"], ballot_par.data.access_uri))

def parse_input(raw_input):
    data  = json.load(sys.stdin)
    poll_expiry = data["until"] if "until" in data else datetime.now() + timedelta(days=1)

    return {
        "poll_name": data["name"],
        "bucket_name": "fn-poll-{0}".format(data["name"]),
        "poll_options": data["options"] if "options" in data else ["Yes", "No"],
        "poll_expiry": rfc3339.format(poll_expiry, utc=True, use_system_timezone=False),
        "poll_description": data["description"] if "description" in data else "",
        "oci_url": os.environ["OCI_REGION_BASE_URL"],
        "compartment_id": os.environ["COMPARTMENT_ID"]
    }

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
        data["namespace_name"], data["bucket_name"], vote_par_details)

def create_ballot_html(object_storage, data, vote_par):
    env = Environment(
        loader=FileSystemLoader("/code/templates"),
        autoescape=select_autoescape(["html", "xml"]))

    ballot_template = env.get_template("ballot.html")

    ballot_html = ballot_template.render(
        options=data["poll_options"],
        expiry=data["poll_expiry"],
        description=data["poll_description"],
        name=data["poll_name"],
        par="{0}{1}".format(data["oci_url"], vote_par.data.access_uri))

    object_storage.put_object(
        data["namespace_name"], data["bucket_name"],
        "ballot.html", ballot_html,
        content_type="text/html",
        defined_tags={"fn-poll": {"ballot": "yes"}})

def create_ballot_par(object_storage, data):
    ballot_par_details = CreatePreauthenticatedRequestDetails(
        name="Ballot {0}".format(data["bucket_name"]),
        object_name="ballot.html",
        access_type="ObjectRead",
        time_expires=data["poll_expiry"])

    return object_storage.create_preauthenticated_request(
        data["namespace_name"], data["bucket_name"], ballot_par_details)

def eprint(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)

if __name__ == "__main__":
    main()
