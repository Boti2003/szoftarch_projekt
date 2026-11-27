package hu.bme.szoftarch.learning.infrastructure.config;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Kimenő RestClient interceptor: a bejövő kérés {@code X-Correlation-Id}
 * headerét propagálja a külső hívásra; ha nincs, újat generál. Így a hívásláncok
 * végigkövethetők a service-ek között.
 */
public class CorrelationIdInterceptor implements ClientHttpRequestInterceptor {

    @Override
    @NonNull
    public ClientHttpResponse intercept(@NonNull org.springframework.http.HttpRequest request,
            @NonNull byte[] body, @NonNull ClientHttpRequestExecution execution) throws IOException {
        if (!request.getHeaders().containsHeader(CorrelationId.HEADER)) {
            request.getHeaders().add(CorrelationId.HEADER, resolveCorrelationId());
        }
        return execution.execute(request, body);
    }

    private String resolveCorrelationId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String incoming = attributes.getRequest().getHeader(CorrelationId.HEADER);
            if (incoming != null && !incoming.isBlank()) {
                return incoming;
            }
        }
        return UUID.randomUUID().toString();
    }
}
