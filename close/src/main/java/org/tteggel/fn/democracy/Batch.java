package org.tteggel.fn.democracy;

import com.fnproject.fn.api.flow.FlowFuture;
import com.fnproject.fn.api.flow.Flows;

import java.util.ArrayList;
import java.util.List;

public class Batch {
    static <T, U> FlowFuture<T> batchList(List<U> list, Integer batchSize,
                                          Flows.SerFunction<List<U>, FlowFuture<T>> compute,
                                          Flows.SerBiFunction<? super T, ? super T, ? extends T> combine) {
        if (list.size() <= batchSize) {
            return compute.apply(list);
        } else {
            List head = new ArrayList<>(list.subList(0, batchSize));
            List tail = new ArrayList<>(list.subList(batchSize, list.size()));
            FlowFuture<T> tailFuture = batchList(tail, batchSize, compute, combine);
            return batchList(head, batchSize, compute, combine).thenCombine(tailFuture, combine);
        }
    }
}
