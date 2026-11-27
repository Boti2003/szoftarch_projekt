package hu.bme.szoftarch.social.infrastructure.learning;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Pragmatic accessor for the current request's correlation id, propagated to
 * Learning internal calls via the {@code X-Correlation-Id} header.
 *
 * <p>Full correlation-id propagation (inbound filter + MDC population) is wired
 * by a later security task (task 11). Until then this reads the id from SLF4J
 * {@link MDC} if present and otherwise mints a fresh UUID so every outbound
 * call still carries a traceable id. The MDC key used here ({@code correlationId})
 * is the one the security task is expected to populate, so this accessor keeps
 * working unchanged once that lands.
 *
 * <p>This type is intentionally scoped to {@code infrastructure.learning} and
 * kept minimal; the Identity adapter has its own equivalent to avoid a shared
 * package that two concurrent changes would both edit.
 */
@Component("learningCorrelationIdAccessor")
public class CorrelationIdAccessor {

    /** MDC key the inbound correlation filter is expected to populate. */
    static final String MDC_KEY = "correlationId";

    /**
     * Returns the current correlation id, or a freshly generated UUID string if
     * none is present in the current context. Never returns {@code null}.
     */
    public String current() {
        String fromContext = MDC.get(MDC_KEY);
        if (fromContext != null && !fromContext.isBlank()) {
            return fromContext;
        }
        return UUID.randomUUID().toString();
    }
}
