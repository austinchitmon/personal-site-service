package com.chitmon.backend.pokemon;

import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Resolves a representative sprite URL from a PokeAPI "sprites" payload,
 * preferring the newest game generation available for that pokemon.
 */
@Service
class PokemonSpriteService {

    private static final Map<Character, Integer> ROMAN_NUMERAL_VALUES =
            Map.of('I', 1, 'V', 5, 'X', 10, 'L', 50, 'C', 100, 'D', 500, 'M', 1000);

    // sprites.versions is keyed by "generation-<roman numeral>", each holding one or
    // more per-game sprite sets (e.g. generation-iv has diamond-pearl, platinum, ...).
    // We walk it to find the highest generation with a usable image, falling back to
    // the newer non-generation-specific artwork if a generation turns out to be empty.
    String resolveNewestSprite(JsonNode sprites) {
        JsonNode versions = sprites.path("versions");
        String newestGenerationKey = null;
        int newestGenerationNumber = -1;
        for (String key : versions.propertyNames()) {
            int number = romanToInt(key.substring("generation-".length()));
            if (number > newestGenerationNumber) {
                newestGenerationNumber = number;
                newestGenerationKey = key;
            }
        }

        if (newestGenerationKey != null) {
            for (JsonNode versionGroup : versions.path(newestGenerationKey).values()) {
                Optional<String> sprite = firstSpriteUrl(versionGroup);
                if (sprite.isPresent()) {
                    return sprite.get();
                }
            }
        }

        return firstSpriteUrl(sprites.path("other").path("official-artwork"))
                .or(() -> firstSpriteUrl(sprites.path("other").path("home")))
                .or(() -> firstSpriteUrl(sprites))
                .orElse(null);
    }

    private Optional<String> firstSpriteUrl(JsonNode spriteSet) {
        JsonNode frontDefault = spriteSet.path("front_default");
        if (frontDefault.isTextual()) {
            return Optional.of(frontDefault.asText());
        }
        for (JsonNode value : spriteSet.values()) {
            if (value.isTextual()) {
                return Optional.of(value.asText());
            }
        }
        return Optional.empty();
    }

    private int romanToInt(String roman) {
        String upper = roman.toUpperCase();
        int result = 0;
        for (int i = 0; i < upper.length(); i++) {
            int value = ROMAN_NUMERAL_VALUES.get(upper.charAt(i));
            int next = i + 1 < upper.length() ? ROMAN_NUMERAL_VALUES.get(upper.charAt(i + 1)) : 0;
            result += value < next ? -value : value;
        }
        return result;
    }
}
