package io.github.hhagenbuch.meter.starter;

import io.github.hhagenbuch.agent.llm.LlmClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

/**
 * Wraps the starter's {@link LlmClient} bean with metering — the "add the dependency, get
 * telemetry" mechanism, zero code changes in the starter.
 *
 * <p>Runs at {@link Ordered#LOWEST_PRECEDENCE} so it applies <em>last</em> and therefore
 * wraps <em>outermost</em>: if another decorator (e.g. a future agent-blackbox recorder)
 * also post-processes the client, meter ends up outside it, matching the DESIGN §7 order
 * (budget/meter outside the recorder). The factory is resolved lazily via
 * {@link ObjectProvider} so this early-instantiated bean doesn't force the meter beans up.
 */
public class LlmClientMeteringBeanPostProcessor implements BeanPostProcessor, Ordered {

    private final ObjectProvider<StarterMeteringFactory> factory;

    public LlmClientMeteringBeanPostProcessor(ObjectProvider<StarterMeteringFactory> factory) {
        this.factory = factory;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof LlmClient client && !(bean instanceof StarterMeteringLlmClient)) {
            return factory.getObject().wrap(client);
        }
        return bean;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
