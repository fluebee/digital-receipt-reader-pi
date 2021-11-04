package main.domain;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;

import main.domain.model.AuthenticationRequest;
import main.domain.model.DigitalReceiptToken;
import main.domain.model.Receipt;
import reactor.core.publisher.Mono;

/**
 * API client to make requests to endpoint in backend
 * 
 * @author Seth Hancock
 * @since November 3, 2021
 */
public class APIClient {
    private DigitalReceiptToken authData;

    /**
     * Default constructor that will authenticate the user for the given username
     * and password. It will then use that token throughout the program in order to
     * be authenticated for request.
     * 
     * @param email    The email of the user.
     * @param password The password associated to that user.
     */
    public APIClient(String email, String password) {
        this.authData = authenticate(email, password);
    }

    /**
     * This will insert a receipt into the database for the given public id which
     * will identity the file on cloudinary.
     * 
     * @param publicId The unique id of the file.
     * @return {@link Receipt} of the inserted data.
     */
    public Receipt insertReceipt(String publicId) {
        return completeRequest(getWebClient().post().uri("/api/receipt-app/receipt")
                .body(Mono.just(new Receipt(publicId)), Receipt.class), Receipt.class);
    }

    /**
     * This will get the next auto incremented value of the receipt details table.
     * 
     * @return {@link Long} of the next auto increment id.
     */
    public long getAutoIncrement() {
        return completeRequest(getWebClient().get().uri("/api/receipt-app/receipt/receipt-details/auto-increment"),
                Long.class);
    }

    /**
     * Metho that will authenticate the request from the constructor.
     * 
     * @return {@link DigitalReceiptToken} with the user and token object.
     */
    private DigitalReceiptToken authenticate(String email, String password) {
        return getWebClient().post().uri("/authenticate")
                .body(Mono.just(new AuthenticationRequest(email, password)), AuthenticationRequest.class).retrieve()
                .bodyToMono(DigitalReceiptToken.class).block();
    }

    /**
     * Helper method that will complete the requets by appending the authorization
     * header data to the request so that it can be authenticated and pass through
     * the JWT Validator.
     * 
     * @param <T>     The object to parse the return data as.
     * @param request The request that needs the authorization header.
     * @param clazz   The class instance to convert the data too.
     * @return {@link T} of the processed data.
     */
    private <T> T completeRequest(RequestHeadersSpec<?> request, Class<T> clazz) {
        return request.header("Authorization", String.format("Bearer: %s", authData.getToken()))
                .accept(MediaType.APPLICATION_JSON).retrieve().bodyToMono(clazz).block();
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
