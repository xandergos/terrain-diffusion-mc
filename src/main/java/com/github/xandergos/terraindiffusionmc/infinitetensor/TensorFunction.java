package com.github.xandergos.terraindiffusionmc.infinitetensor;

import java.util.List;

/**
 * Function that computes a window of an InfiniteTensor.
 *
 * @param windowIndex the N-dimensional index of the window being computed
 * @param args        slices from each upstream dependency tensor, in the order declared
 * @return the computed FloatTensor with shape matching the output TensorWindow size
 */
@FunctionalInterface
public interface TensorFunction {
    FloatTensor apply(int[] windowIndex, List<FloatTensor> args);
}
