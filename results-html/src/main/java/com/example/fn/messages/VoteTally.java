package com.example.fn.messages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VoteTally extends HashMap<String, Integer> {
    public Map<String, Map<String, Integer>> toTemplateData() {
        Map result = new HashMap<>();
        List votes = new ArrayList();
        this.forEach((k, v) -> {
            Map vote = new HashMap();
            vote.put("option", k);
            vote.put("tally", v);
            votes.add(vote);
        });
        result.put("results", votes);
        return result;
    }
}
