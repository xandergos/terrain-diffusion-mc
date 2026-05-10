package com.github.xandergos.terraindiffusionmc.infinitetensor;

import java.util.List;

/**
 * Batched variant of TensorFunction.
 */
@FunctionalInterface
public interface BatchTensorFunction {
    /**
     * Applies the function to a batch of tensor windows.
     *
     * @param windowIndices the window indices for the batch
     * @param args args.get(depIdx) is the list of dependency slices, one per window in the batch
     * @return list of output tensors, one per window in the batch
     */
    List<FloatTensor> apply(List<int[]> windowIndices, List<List<FloatTensor>> args);
}
