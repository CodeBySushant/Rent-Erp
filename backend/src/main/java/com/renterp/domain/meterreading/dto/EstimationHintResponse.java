package com.renterp.domain.meterreading.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class EstimationHintResponse {

    private final UUID meterId;
    private final BigDecimal rollingAverage;    // arithmetic mean of the sampled consumptions; NULL if none available
    private final int sampleSize;               // how many readings contributed
    private final List<Sample> samples;         // most-recent-first

    @Getter
    @Builder
    public static class Sample {
        private final UUID readingId;
        private final String readingDateBs;
        private final BigDecimal consumption;
    }
}
