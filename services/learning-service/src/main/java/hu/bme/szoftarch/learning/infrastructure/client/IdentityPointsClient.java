package hu.bme.szoftarch.learning.infrastructure.client;

import hu.bme.szoftarch.learning.domain.port.AwardPointsCommand;
import hu.bme.szoftarch.learning.domain.port.AwardPointsResult;
import hu.bme.szoftarch.learning.domain.port.IdentityPointsPort;
import hu.bme.szoftarch.learning.infrastructure.security.InternalApiKeyFilter;
import hu.bme.szoftarch.learning.infrastructure.security.InternalApiProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Az {@link IdentityPointsPort} RestClient adaptere (TERV B – U5, Adapter minta).
 * {@code POST {IDENTITY_URL}/internal/v1/users/{userId}/points} hívást küld az
 * Identity service-nek, {@link IdentityPointsRequest} body-val és
 * {@code X-Internal-Api-Key} headerrel (TERV 8.). A {@code defaultRestClient}
 * timeoutjait használja (connect 2s / read 5s, U0).
 *
 * <p>Kimenet-leképezés (TERV 7.8): 2xx → {@code AWARDED}; 4xx → nem
 * újrapróbálható; 5xx / connect+read timeout / hálózati hiba → újrapróbálható.</p>
 *
 * <p><strong>Biztonság:</strong> a belső API-kulcs, a token és a body SOHA nem
 * kerül logba; csak a kimenet és a HTTP-státusz naplózódik.</p>
 */
@Component
public class IdentityPointsClient implements IdentityPointsPort {

    private static final Logger log = LoggerFactory.getLogger(IdentityPointsClient.class);

    private final RestClient restClient;
    private final InternalApiProperties internalApiProperties;
    private final IdentityClientProperties identityProperties;

    public IdentityPointsClient(RestClient defaultRestClient, InternalApiProperties internalApiProperties,
            IdentityClientProperties identityProperties) {
        this.restClient = defaultRestClient;
        this.internalApiProperties = internalApiProperties;
        this.identityProperties = identityProperties;
    }

    @Override
    public AwardPointsResult award(AwardPointsCommand command) {
        IdentityPointsRequest body = new IdentityPointsRequest(
                command.sourceType(), command.sourceId(), command.points(), command.idempotencyKey());
        String uri = identityProperties.url() + "/internal/v1/users/" + command.userId() + "/points";
        try {
            restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(InternalApiKeyFilter.HEADER, internalApiProperties.apiKey())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Identity point award succeeded: sourceType={} status=AWARDED", command.sourceType());
            return AwardPointsResult.AWARDED;
        } catch (RestClientResponseException e) {
            // Megérkezett HTTP-státusz: 4xx nem újrapróbálható, 5xx újrapróbálható.
            int status = e.getStatusCode().value();
            if (e.getStatusCode().is4xxClientError()) {
                log.warn("Identity point award rejected (non-retryable): status={}", status);
                return AwardPointsResult.NON_RETRYABLE_ERROR;
            }
            log.warn("Identity point award failed (retryable): status={}", status);
            return AwardPointsResult.RETRYABLE_ERROR;
        } catch (Exception e) {
            // Connect/read timeout vagy hálózati hiba (ResourceAccessException stb.): újrapróbálható.
            log.warn("Identity point award failed (retryable, no response): {}", e.getClass().getSimpleName());
            return AwardPointsResult.RETRYABLE_ERROR;
        }
    }
}
