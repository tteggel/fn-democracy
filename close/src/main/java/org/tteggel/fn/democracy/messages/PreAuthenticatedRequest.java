package org.tteggel.fn.democracy.messages;

import com.oracle.bmc.objectstorage.model.PreauthenticatedRequestSummary;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PreAuthenticatedRequest implements Serializable {
    public String id;
    public Date timeExpires;

    private PreAuthenticatedRequest(String id, Date timeExpires) {
        this.id = id;
        this.timeExpires = timeExpires;
    }

    public static List<PreAuthenticatedRequest> buildList(List<PreauthenticatedRequestSummary> pars) {
        List<PreAuthenticatedRequest> result = new ArrayList<>();
        for (PreauthenticatedRequestSummary par: pars) {
            result.add(new PreAuthenticatedRequest(par.getId(), par.getTimeExpires()));
        }
        return result;
    }
}
