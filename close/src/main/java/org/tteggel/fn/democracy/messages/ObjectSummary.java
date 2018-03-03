package org.tteggel.fn.democracy.messages;

import com.oracle.bmc.objectstorage.model.ListObjects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ObjectSummary implements Serializable {

    public String name;

    private ObjectSummary(String name) {
        this.name = name;
    }

    public static List<ObjectSummary> buildList(ListObjects objects) {
        List<ObjectSummary> result = new ArrayList<>();
        for(com.oracle.bmc.objectstorage.model.ObjectSummary obj: objects.getObjects()) {
            result.add(new ObjectSummary(obj.getName()));
        }
        return result;
    }
}
