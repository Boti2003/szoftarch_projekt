package hu.bme.szoftarch.learning.domain.port;

/**
 * Kimenő domain port a pontjóváíráshoz az Identity service felé (TERV B – U5,
 * KÖTELEZŐ Adapter minta). A domain csak ezt a Spring-mentes interfészt ismeri;
 * a konkrét RestClient adapter az infrastructure/client rétegben él.
 *
 * <p>A hívás tűz-és-felejt jellegű, idempotenciakulccsal (TERV 5.3): azonos
 * kulcs ismételt beküldése nem ad új pontot. A learning oldalon az eredmény
 * státuszát a saját attempt rekord követi.</p>
 */
public interface IdentityPointsPort {

    /**
     * Elküldi a pontjóváírási parancsot az Identity service-nek.
     *
     * @param command a jóváírás paraméterei (user, forrás, pont, idempotenciakulcs)
     * @return a kimenet: jóváírva, újrapróbálható hiba, vagy nem újrapróbálható hiba
     */
    AwardPointsResult award(AwardPointsCommand command);
}
