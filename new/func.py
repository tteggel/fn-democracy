import sys, json

from oci.config import from_file, validate_config
from oci.regions import endpoint_for
from oci.object_storage.object_storage_client import ObjectStorageClient
from oci.object_storage.models import CreateBucketDetails, CreatePreauthenticatedRequestDetails
from oci.exceptions import ServiceError

from datetime import timedelta, datetime
import rfc3339

from jinja2 import Environment, FileSystemLoader, select_autoescape

def eprint(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)

data  = json.load(sys.stdin)
bucket_name = "fn-poll-{0}".format(data['name'])
poll_options = data["options"] if "options" in data else ["Yes", "No"]
eprint("Options: {0}".format(poll_options))
poll_expiry = data["until"] if "until" in data else datetime.now() + timedelta(days=1)
poll_expiry_rfc3339 = rfc3339.format(poll_expiry, utc=True, use_system_timezone=False)
eprint("Expiry: {0}".format(poll_expiry))

config = from_file(file_location="/code/oci/config")
validate_config(config)

object_storage = ObjectStorageClient(config)
namespace_name = object_storage.get_namespace().data

create_bucket_details = CreateBucketDetails(
    name=bucket_name,
    compartment_id="ocid1.compartment.oc1..aaaaaaaair52lxrna4lgquxmuzbcqegey7njbu64chcshywvw7pwrx6e7xsa",
    public_access_type="NoPublicAccess",
    storage_tier="Standard")
try:
    object_storage.create_bucket(namespace_name, create_bucket_details)
except ServiceError as e:
    eprint(e)
    if(e.code != "BucketAlreadyExists"): raise

vote_par_details = CreatePreauthenticatedRequestDetails(
    name="Vote {0}".format(bucket_name),
    access_type="AnyObjectWrite",
    time_expires=poll_expiry_rfc3339)
vote_par = object_storage.create_preauthenticated_request(
    namespace_name, bucket_name, vote_par_details)

env = Environment(
    loader=FileSystemLoader("/code/templates"),
    autoescape=select_autoescape(['html', 'xml'])
)
ballot = env.get_template("ballot.html").render(options=poll_options,
                                                expiry=poll_expiry,
                                                par="https://objectstorage.us-ashburn-1.oraclecloud.com{0}".format(vote_par.data.access_uri))
object_storage.put_object(namespace_name, bucket_name, "ballot.html", ballot,
                          content_type="text/html")

ballot_par_details = CreatePreauthenticatedRequestDetails(
    name="Ballot {0}".format(bucket_name),
    object_name="ballot.html",
    access_type="ObjectRead",
    time_expires=poll_expiry_rfc3339)
ballot_par = object_storage.create_preauthenticated_request(
    namespace_name, bucket_name, ballot_par_details)

print("https://objectstorage.us-ashburn-1.oraclecloud.com{0}".format(ballot_par.data.access_uri))
