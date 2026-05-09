/*
 * Copyright 2024 TSAIL Team and The HuggingFace Team. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * DISCLAIMER: This file is strongly influenced by
 * https://github.com/LuChengTHU/dpm-solver and https://github.com/NVlabs/edm
 */
package com.github.xandergos.terraindiffusionmc.pipeline;

/**
 * Port of EDMDPMSolverMultistepScheduler (dpmsolver.py) with Karras sigma schedule
 * and DPM-Solver++ second-order multistep update.
 *
 * <p>Only the "dpmsolver++" + "midpoint" + "karras" configuration is implemented,
 * as that is what WorldPipeline uses for the 20-step coarse model.
 */
public final class EDMScheduler {

    public static final float SIGMA_DATA = 0.5f;
    public static final float SIGMA_MIN = 0.002f;
    public static final float SIGMA_MAX = 80.0f;
    private static final float RHO = 7.0f;

    /** Sigmas array (numSteps + 1 entries, last entry is 0). */
    public final float[] sigmas;
    /** Timesteps array (c_noise = 0.25 * log(sigma)), same length as numSteps. */
    public final float[] timesteps;
    private final int numSteps;

    // State for DPM-Solver++ multi-step
    private int stepIndex = 0;
    private int lowerOrderNums = 0;
    private float[] prevModelOutput = null;  // x0_pred from previous step

    public EDMScheduler(int numSteps) {
        this.numSteps = numSteps;
        this.sigmas = computeKarrasSignas(numSteps);
        this.timesteps = new float[numSteps];
        for (int i = 0; i < numSteps; i++) {
            timesteps[i] = 0.25f * (float) Math.log(sigmas[i]);
        }
        reset();
    }

    public void reset() {
        stepIndex = 0;
        lowerOrderNums = 0;
        prevModelOutput = null;
    }

    /** c_in scaling: sample / sqrt(sigma^2 + sigma_data^2). */
    public static void preconditionInputsInPlace(float[] sample, float sigma) {
        float cIn = 1.0f / (float) Math.sqrt(sigma * sigma + SIGMA_DATA * SIGMA_DATA);
        for (int i = 0; i < sample.length; i++) sample[i] *= cIn;
    }

    public static float[] preconditionInputs(float[] sample, float sigma) {
        float[] out = sample.clone();
        preconditionInputsInPlace(out, sigma);
        return out;
    }

    /** trigflow_precondition_noise: atan(sigma / sigma_data). */
    public static float trigflowPreconditionNoise(float sigma) {
        return (float) Math.atan(sigma / SIGMA_DATA);
    }

    /**
     * Convert raw model output to x0_pred (denoised) using the EDM precondition_outputs formula.
     * c_skip = sigma_data^2 / (sigma^2 + sigma_data^2)
     * c_out  = sigma * sigma_data / sqrt(sigma^2 + sigma_data^2)
     */
    public static float[] preconditionOutputs(float[] sample, float[] modelOut, float sigma) {
        float sd2 = SIGMA_DATA * SIGMA_DATA;
        float sig2 = sigma * sigma;
        float cSkip = sd2 / (sig2 + sd2);
        float cOut = sigma * SIGMA_DATA / (float) Math.sqrt(sig2 + sd2);
        float[] x0 = new float[sample.length];
        for (int i = 0; i < sample.length; i++) {
            x0[i] = cSkip * sample[i] + cOut * modelOut[i];
        }
        return x0;
    }

    /**
     * Run one DPM-Solver++ step. Returns prev_sample.
     *
     * @param modelOut raw model output for current step
     * @param sample   current noisy sample
     * @return denoised sample at previous (lower) sigma
     */
    public float[] step(float[] modelOut, float[] sample) {
        float sigmaS = sigmas[stepIndex];
        float sigmaT = sigmas[stepIndex + 1];

        float[] x0Pred = preconditionOutputs(sample, modelOut, sigmaS);

        boolean isLastStep = (stepIndex == numSteps - 1);
        // lower_order_final: always true at last step (final_sigmas_type == "zero")
        boolean lowerOrderFinal = isLastStep;

        float[] prevSample;
        if (lowerOrderNums < 1 || lowerOrderFinal) {
            prevSample = firstOrderUpdate(x0Pred, sample, sigmaS, sigmaT);
        } else {
            float sigmaS1 = sigmas[stepIndex - 1];
            prevSample = secondOrderUpdate(prevModelOutput, x0Pred, sample, sigmaS, sigmaT, sigmaS1);
        }

        prevModelOutput = x0Pred;
        if (lowerOrderNums < 2) lowerOrderNums++;
        stepIndex++;
        return prevSample;
    }

    /**
     * DPM-Solver++ first-order update.
     * Python uses _sigma_to_alpha_sigma_t returning (alpha=1, sigma_t=sigma) — no VP conversion.
     * lambda = log(alpha) - log(sigma) = -log(sigma)
     * h = lambda_t - lambda_s = log(sigma_s / sigma_t)
     * exp(-h) = sigma_t / sigma_s
     * x_t = (sigma_t/sigma_s)*sample - (exp(-h) - 1)*D0
     *      = (sigma_t/sigma_s)*sample - (sigma_t/sigma_s - 1)*D0
     */
    private static float[] firstOrderUpdate(float[] x0Pred, float[] sample, float sigmaS, float sigmaT) {
        float ratio = sigmaT / sigmaS;  // exp(-h) = sigma_t / sigma_s
        float[] xt = new float[sample.length];
        for (int i = 0; i < sample.length; i++) {
            xt[i] = ratio * sample[i] - (ratio - 1.0f) * x0Pred[i];
        }
        return xt;
    }

    /**
     * DPM-Solver++ second-order midpoint.
     * Python: alpha_t = 1, lambda = -log(sigma)
     * h = lambda_t - lambda_s0, h0 = lambda_s0 - lambda_s1, r0 = h0/h
     * D0 = m0, D1 = (m0 - m1) / r0
     * x_t = (sigma_t/sigma_s0)*sample - (exp(-h)-1)*D0 - 0.5*(exp(-h)-1)*D1
     */
    private static float[] secondOrderUpdate(float[] m1, float[] m0, float[] sample,
                                              float sigmaS0, float sigmaT, float sigmaS1) {
        double lT  = -Math.log(sigmaT);
        double lS0 = -Math.log(sigmaS0);
        double lS1 = -Math.log(sigmaS1);
        double h   = lT - lS0;
        double h0  = lS0 - lS1;
        float r0   = (float) (h0 / h);
        float expNH    = sigmaT / sigmaS0;  // = exp(-h) in float32 like Python
        float sCoeff   = sigmaT / sigmaS0;
        float d0Coeff  = -(expNH - 1.0f);
        float d1Coeff  = -0.5f * (expNH - 1.0f);
        float[] xt = new float[sample.length];
        for (int i = 0; i < sample.length; i++) {
            float d1 = (m0[i] - m1[i]) / r0;
            xt[i] = sCoeff * sample[i] + d0Coeff * m0[i] + d1Coeff * d1;
        }
        return xt;
    }

    /** Karras sigma schedule: sigmas[i] = (max_inv + i/(n-1)*(min_inv - max_inv))^rho + trailing 0 */
    public static float[] computeKarrasSignas(int n) {
        float minInvRho = (float) Math.pow(SIGMA_MIN, 1.0 / RHO);
        float maxInvRho = (float) Math.pow(SIGMA_MAX, 1.0 / RHO);
        float[] sigmas = new float[n + 1];
        for (int i = 0; i < n; i++) {
            float t = (float) i / (n - 1);
            float invRho = maxInvRho + t * (minInvRho - maxInvRho);
            sigmas[i] = (float) Math.pow(invRho, RHO);
        }
        sigmas[n] = 0.0f;  // final_sigmas_type == "zero"
        return sigmas;
    }
}
