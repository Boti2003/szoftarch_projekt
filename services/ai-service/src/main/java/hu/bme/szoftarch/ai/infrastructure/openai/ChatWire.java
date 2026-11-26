package hu.bme.szoftarch.ai.infrastructure.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Az OpenAI-kompatibilis chat completions API drótformátuma (request/response).
 * Csak a felhasznált mezőket tartalmazza; az ismeretleneket figyelmen kívül hagyjuk.
 */
public final class ChatWire {

    private ChatWire() {
    }

    public record ChatMessage(String role, String content) {
    }

    /**
     * A JSON-kimenet kikényszerítése: {"type":"json_object"} a válaszformátumra.
     */
    public record ResponseFormat(String type) {
        public static ResponseFormat jsonObject() {
            return new ResponseFormat("json_object");
        }
    }

    public record ChatRequest(
            String model,
            List<ChatMessage> messages,
            @JsonProperty("response_format") ResponseFormat responseFormat,
            Double temperature) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatResponse(List<Choice> choices) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Choice(ChatMessage message) {
        }

        /** Az első választás tartalma, vagy null, ha nincs. */
        public String firstContent() {
            if (choices == null || choices.isEmpty() || choices.get(0).message() == null) {
                return null;
            }
            return choices.get(0).message().content();
        }
    }
}
