package com.example.fn.messages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BallotData {
    public BallotData(){}
    public List<VoteOption> results;

    public static class VoteOption  {
        public VoteOption(){}
        public String option;
        public Integer tally;
    }

    public Map<String, Map<String, Integer>> toTemplateData() {
        Map result = new HashMap<>();
        List votes = new ArrayList();
        results.forEach((option) -> {
            Map vote = new HashMap();
            vote.put("option", option.option);
            vote.put("tally", option.tally);
            votes.add(vote);
        });
        result.put("results", votes);
        return result;
    }
}
