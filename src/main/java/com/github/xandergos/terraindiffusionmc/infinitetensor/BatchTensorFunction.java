package com.github.xandergos.terraindiffusionmc.infinitetensor;

import java.util.List;

/**
 * Batched variant of TensorFunction.
 *
 * @param windowIndices the window indices for the batch
 * @param args          args.get(depIdx) is the list of dependency slices — one per window in the batch
 * @return list of output tensors, one per window in the batch
 */
@FunctionalInterface
public interface BatchTensorFunction {
    List<FloatTensor> apply(List<int[]> windowIndices, List<List<FloatTensor>> args);
}
