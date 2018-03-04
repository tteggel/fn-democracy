package org.tteggel.fn.democracy.messages;

import java.util.List;

public class BallotData {
    public BallotData(){}
    public List<VoteOption> results;

    public static class VoteOption  {
        public VoteOption(){}
        public String option;
        public Integer tally;
    }
}
