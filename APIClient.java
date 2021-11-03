import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import model.AuthenticationRequest;
import model.DigitalReceiptToken;
import model.Receipt;
import reactor.core.publisher.Mono;

/**
 * API client to make requests to endpoint in backend
 * 
 * @author Seth Hancock
 * @since November 3, 2021
 */
public class APIClient {

    // public <T> Receipt post(String endpoint, Receipt body, Class<T> clazz) {
    // getWebClient().post().uri(endpoint).body(Mono.just(body), Receipt.class);
    // }

    public static DigitalReceiptToken authenticate() {
        return getWebClient().post().uri("/authenticate")
                .body(Mono.just(new AuthenticationRequest("sambutler1017@icloud.com", "100698Sb!")),
                        AuthenticationRequest.class)
                .retrieve().bodyToMono(DigitalReceiptToken.class).block();
    }

    public static Receipt insertReceipt(String publicId, String token) {
        return getWebClient().post().uri("/api/receipt-app/receipt")
                .body(Mono.just(new Receipt(publicId)), Receipt.class)
                .header("Authorization", String.format("Bearer: %s", token)).accept(MediaType.APPLICATION_JSON)
                .retrieve().bodyToMono(Receipt.class).block();
    }

    public static long getAutoIncrement(String token) {
        return getWebClient().get().uri("/api/receipt-app/receipt/receipt-details/auto-increment")
                .header("Authorization", String.format("Bearer: %s", token)).accept(MediaType.APPLICATION_JSON)
                .retrieve().bodyToMono(Long.class).block();
    }

    /**
     * Creates the base webclient builder.
     * 
     * @return {@link WebClient.Builder} object with the base url.
     */
    private static WebClient getWebClient() {
        return WebClient.builder().baseUrl("https://digital-receipt-production.herokuapp.com").build();
    }
}
