package org.tteggel.fn.democracy;

import com.fnproject.fn.api.InputEvent;
import com.fnproject.fn.api.RuntimeContext;
import com.fnproject.fn.api.flow.Flow;
import com.fnproject.fn.api.flow.FlowFuture;
import com.fnproject.fn.api.flow.Flows;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.ListObjects;
import com.oracle.bmc.objectstorage.requests.*;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.tteggel.fn.democracy.messages.ObjectSummary;
import org.tteggel.fn.democracy.messages.PreAuthenticatedRequest;
import org.tteggel.fn.democracy.messages.VoteTally;

import java.io.*;
import java.util.*;

public class Close implements Serializable {

    private static final String POLL_NOT_CLOSED_ERROR = "There are still Pre-Authenticated Requests set for this bucket which means that the poll is still open and cannot be counted yet.";
    private String namespaceName;
    private String resultsBucket;

    public Close(RuntimeContext ctx) throws IOException {
        namespaceName = objectStorage().getNamespace(
                GetNamespaceRequest.builder().build()).getValue();

        resultsBucket = ctx.getConfigurationByKey("RESULTS_BUCKET")
                .orElseThrow(() -> new IllegalArgumentException("Config RESULTS_BUCKET must be defined."));
    }

    public String main(String input, InputEvent rawEvent) {
        String pollId = rawEvent.getQueryParameters().get("i")
                .orElseThrow(IllegalArgumentException::new);

        Flows.currentFlow().supply(() -> listPars(pollId))
        .thenCompose((pars) -> deletePars(pollId, pars))
        .thenCompose((ignored) -> getTally(pollId, null))
        .thenCompose((tally) -> writeResults(pollId, tally))
        .thenCompose((ignored) -> deletePoll(pollId, null));

        return input;
    }

    private List<PreAuthenticatedRequest> listPars(String pollId) {

        ListPreauthenticatedRequestsRequest lprr =
            ListPreauthenticatedRequestsRequest.builder()
            .namespaceName(namespaceName)
            .bucketName(pollId)
            .build();

        return PreAuthenticatedRequest.buildList(
                objectStorage()
                    .listPreauthenticatedRequests(lprr)
                    .getItems());
    }

    private FlowFuture<Void> deletePars(String pollId, List<PreAuthenticatedRequest> pars) {
        Flow f = Flows.currentFlow();
        List<FlowFuture<Void>> deletions = new ArrayList<>();
        Date now = new Date();

        for (PreAuthenticatedRequest par : pars) {
            if (par.timeExpires.after(now)) {
                return f.failedFuture(new IllegalStateException(POLL_NOT_CLOSED_ERROR));
            } else {
                deletions.add(f.supply(() -> {
                    String parId = par.id;
                    DeletePreauthenticatedRequestRequest dprr =
                        DeletePreauthenticatedRequestRequest.builder()
                        .namespaceName(namespaceName)
                        .bucketName(pollId)
                        .parId(parId)
                        .build();

                    objectStorage().deletePreauthenticatedRequest(dprr);
                }));
            }
        }

        if (deletions.size() == 0) {
            return f.completedValue(null);
        } else {
            return f.allOf(deletions.toArray(new FlowFuture[deletions.size()]));
        }
    }

    private FlowFuture<VoteTally> getTally(String pollId, String start) {
        ListObjectsRequest lor = ListObjectsRequest.builder()
            .namespaceName(namespaceName)
            .bucketName(pollId)
            .start(start)
            .build();

        ListObjects listObjects = objectStorage().listObjects(lor).getListObjects();
        List<ObjectSummary> objectSummaries = ObjectSummary.buildList(listObjects);

        FlowFuture<VoteTally> tally = getVoteTally(pollId, objectSummaries);

        String nextStartWith = listObjects.getNextStartWith();
        if (nextStartWith == null) {
            return tally;
        } else {
            FlowFuture<VoteTally> nextTallyFuture = getTally(pollId, nextStartWith);
            return tally.thenCombine(nextTallyFuture, Close::combineTally);
        }
    }

    private FlowFuture<VoteTally> getVoteTally(String pollId, List<ObjectSummary> ballotList) {
        Integer batchSize = 10;
        if (ballotList.size() <= batchSize) {
            return Flows.currentFlow().supply(() -> getVotes(pollId, ballotList))
                    .thenApply(Close::computeTally);
        } else {
            List<ObjectSummary> head = new ArrayList<>(ballotList.subList(0, batchSize));
            List<ObjectSummary> tail = new ArrayList<>(ballotList.subList(batchSize, ballotList.size()));
            FlowFuture<VoteTally> tailFuture = getVoteTally(pollId, tail);
            return getVoteTally(pollId, head).thenCombine(tailFuture, Close::combineTally);
        }
    }

    private List<String> getVotes(String pollId, List<ObjectSummary> objs) {
        List<String> result = new ArrayList<>();
        System.err.println("Getting votes for " + objs.size() + "ballots.");
        for (ObjectSummary s : objs) {
            if (s.name.equals("ballot.html") || s.name.equals("ballot.json")) { continue; }
            result.add(getVote(pollId, s));
        }

        return result;
    }

    private String getVote(String pollId, ObjectSummary s) {
        GetObjectRequest gor = GetObjectRequest.builder()
            .namespaceName(namespaceName)
            .bucketName(pollId)
            .objectName(s.name)
            .build();

        GetObjectResponse object = objectStorage().getObject(gor);

        String result = streamToString(object.getInputStream());
        System.err.println("Got vote: " + result);

        return result;
    }

    private static VoteTally computeTally(List<String> getVoteList) {
            VoteTally tally = new VoteTally();

            getVoteList.forEach((vote) ->
                tally.compute(vote, (k, v) -> (v == null ? 1 : v + 1))
            );

            return tally;
    }

    private static VoteTally combineTally(VoteTally a, VoteTally b) {
        b.forEach((k, v) -> a.merge(k, v, Integer::sum));
        return a;
    }

    private FlowFuture<Void> writeResults(String pollId, VoteTally tally)  {
        System.err.println("Writing results for poll: " + pollId);
        System.err.println(tally);

        Template template;
        try {
            template = templates().getTemplate("results.html");
        } catch (IOException e) {
            return Flows.currentFlow().failedFuture(e);
        }

        Map<String, List> data = new HashMap<>();
        List<Map<String, String>> voteList = new ArrayList<>();
        tally.forEach((k, v) -> {
            Map<String, String> entry = new HashMap<>();
            entry.put("option", k);
            entry.put("tally", v.toString());
            voteList.add(entry);
        });
        data.put("results", voteList);

        try {

            PipedInputStream inStream = new PipedInputStream();
            PipedOutputStream outStream = new PipedOutputStream(inStream);
            OutputStreamWriter writer = new OutputStreamWriter(outStream);
            PutObjectRequest por = PutObjectRequest.builder()
                .namespaceName(namespaceName)
                .bucketName(resultsBucket)
                .objectName(pollId + ".html")
                .contentType("text/html")
                .putObjectBody(inStream)
                .build();
            template.process(data, writer);
            writer.flush(); writer.close();
            objectStorage().putObject(por);
        } catch (Exception e) {
            return Flows.currentFlow().failedFuture(e);
        }

        return Flows.currentFlow().completedValue(null);
    }




    private FlowFuture<Void> deletePoll(String pollId, String start) {
        System.err.println("Deleting votes and ballot for poll: " + pollId);
        ListObjectsRequest lor = ListObjectsRequest.builder()
            .namespaceName(namespaceName)
            .bucketName(pollId)
            .start(start)
            .build();

        ListObjects listObjects = objectStorage().listObjects(lor).getListObjects();
        FlowFuture<Void> deletes = deleteObjects(pollId, ObjectSummary.buildList(listObjects));

        String nextStartWith = listObjects.getNextStartWith();
        if(nextStartWith == null) {
            return deletes.thenRun(() -> deleteBucket(pollId));
        } else {
            return deletePoll(pollId, nextStartWith).thenCompose((ignored) -> deletes);
        }
    }

    private FlowFuture<Void> deleteObjects(String pollId, List<ObjectSummary> objects) {
        Integer batchSize = 10;
        if (objects.size() <= batchSize) {
            return Flows.currentFlow().supply(() -> {
                for(ObjectSummary s: objects) {
                    DeleteObjectRequest dor = DeleteObjectRequest.builder()
                            .namespaceName(namespaceName)
                            .bucketName(pollId)
                            .objectName(s.name)
                            .build();
                    objectStorage().deleteObject(dor);
                }
            });
        } else {
            List<ObjectSummary> head = new ArrayList<>(objects.subList(0, batchSize));
            List<ObjectSummary> tail = new ArrayList<>(objects.subList(batchSize, objects.size()));
            FlowFuture<Void> tailFuture = deleteObjects(pollId, tail);
            return deleteObjects(pollId, head).thenCompose((ignored) -> tailFuture);
        }
    }

    private void deleteBucket(String pollId) {
        DeleteBucketRequest dbr = DeleteBucketRequest.builder()
                .namespaceName(namespaceName)
                .bucketName(pollId)
                .build();
        objectStorage().deleteBucket(dbr);
    }

    private ObjectStorageClient objectStorage() {
        InputStream configStream = getClass().getResourceAsStream("/config");
        ConfigFileReader.ConfigFile config = null;
        try {
            config = ConfigFileReader.parse(configStream, "DEFAULT");
        } catch (IOException e) {
            System.err.println(e.getStackTrace());
        }

        Supplier<InputStream> privateKeySupplier =
                Suppliers.ofInstance( getClass().getResourceAsStream("/oci_api_key.pem"));

        AuthenticationDetailsProvider authenticationDetailsProvider = SimpleAuthenticationDetailsProvider.builder()
                .tenantId(config.get("tenancy"))
                .userId(config.get("user"))
                .fingerprint(config.get("fingerprint"))
                .privateKeySupplier(privateKeySupplier)
                .build();

        ObjectStorageClient result = new ObjectStorageClient(authenticationDetailsProvider);
        result.setRegion(Region.US_ASHBURN_1);

        return result;
    }

    private Configuration templates() {
        Configuration templateConfig = new Configuration(Configuration.VERSION_2_3_27);
        templateConfig.setDefaultEncoding("UTF-8");
        templateConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        templateConfig.setLogTemplateExceptions(false);
        templateConfig.setWrapUncheckedExceptions(true);
        templateConfig.setClassForTemplateLoading(Close.class, "/");
        return templateConfig;
    }

    private String streamToString(InputStream stream) {
        Scanner scanner = new Scanner(stream).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }
}