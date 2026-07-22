package io.github.hhagenbuch.meter.spring.otel;

import io.github.hhagenbuch.meter.core.attr.MeterAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SliInstrumentsTest {

    private final InMemoryMetricReader reader = InMemoryMetricReader.create();
    private final SdkMeterProvider meterProvider = SdkMeterProvider.builder()
            .registerMetricReader(reader).build();
    private final Instruments instruments = new Instruments(meterProvider.get("t"));

    private final Attributes dims = Attributes.builder()
            .put(MeterAttributes.SLI_DATASET, "customer-support")
            .put(MeterAttributes.PROMPT_VERSION, "support-v2")
            .build();

    @Test
    void evalRunSplitsCasesByResultAndRecordsTheRunRate() {
        instruments.recordEvalRun(4, 6, dims);

        MetricData cases = metric("agent.sli.eval_cases");
        assertThat(cases.getLongSumData().getPoints())
                .extracting(p -> p.getAttributes().get(MeterAttributes.SLI_RESULT), LongPointData::getValue)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("pass", 4L),
                        org.assertj.core.groups.Tuple.tuple("fail", 2L));
        // Both series keep the caller's attribution dims.
        assertThat(cases.getLongSumData().getPoints())
                .allSatisfy(p -> assertThat(p.getAttributes().get(MeterAttributes.SLI_DATASET))
                        .isEqualTo("customer-support"));

        HistogramPointData rate = metric("agent.sli.eval_pass_rate")
                .getHistogramData().getPoints().iterator().next();
        assertThat(rate.getSum()).isCloseTo(4.0 / 6.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(rate.getCount()).isEqualTo(1);
    }

    @Test
    void countersAccumulateAcrossRunsSoWindowedMathIsExact() {
        instruments.recordEvalRun(6, 6, dims);
        instruments.recordEvalRun(4, 6, dims);

        List<LongPointData> points = List.copyOf(metric("agent.sli.eval_cases").getLongSumData().getPoints());
        long pass = points.stream()
                .filter(p -> "pass".equals(p.getAttributes().get(MeterAttributes.SLI_RESULT)))
                .mapToLong(LongPointData::getValue).sum();
        long fail = points.stream()
                .filter(p -> "fail".equals(p.getAttributes().get(MeterAttributes.SLI_RESULT)))
                .mapToLong(LongPointData::getValue).sum();
        assertThat(pass).isEqualTo(10);
        assertThat(fail).isEqualTo(2);
    }

    @Test
    void degenerateRunsRecordNothing() {
        instruments.recordEvalRun(0, 0, dims);   // empty run: no evidence, not a pass
        instruments.recordEvalRun(-1, 6, dims);  // nonsense from a broken parser
        instruments.recordEvalRun(7, 6, dims);

        assertThat(reader.collectAllMetrics())
                .noneMatch(m -> m.getName().startsWith("agent.sli."));
    }

    private MetricData metric(String name) {
        Collection<MetricData> all = reader.collectAllMetrics();
        return all.stream().filter(m -> m.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError(name + " not found in " + all));
    }
}
