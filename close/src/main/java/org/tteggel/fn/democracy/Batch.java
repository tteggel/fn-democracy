package org.tteggel.fn.democracy;

import com.fnproject.fn.api.flow.FlowFuture;
import com.fnproject.fn.api.flow.Flows;

import java.util.ArrayList;
import java.util.List;

class Batch {
    static <T, U> FlowFuture<T> batchList(List<U> list, Integer batchSize,
                                          Flows.SerFunction<List<U>, FlowFuture<T>> processBatch,
                                          Flows.SerBiFunction<? super T, ? super T, ? extends T> combineBatch) {
        if (list.size() <= batchSize) {
            return processBatch.apply(list);
        } else {
            List<U> head = new ArrayList<>(list.subList(0, batchSize));
            List<U> tail = new ArrayList<>(list.subList(batchSize, list.size()));
            FlowFuture<T> tailFuture = batchList(tail, batchSize, processBatch, combineBatch);
            return batchList(head, batchSize, processBatch, combineBatch)
                    .thenCombine(tailFuture, combineBatch);
        }
    }
}