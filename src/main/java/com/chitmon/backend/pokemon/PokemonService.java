package com.chitmon.backend.pokemon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class PokemonService {

    private static final Duration SPECIES_COUNT_CACHE_TTL = Duration.ofHours(6);

    private final WebClient pokeApiWebClient;
    private final PokemonSpriteService spriteService;
    private final Mono<Integer> speciesCount;

    public PokemonService(WebClient pokeApiWebClient, PokemonSpriteService spriteService) {
        this.pokeApiWebClient = pokeApiWebClient;
        this.spriteService = spriteService;
        // Species IDs are contiguous from 1..count, so any id in that range is
        // guaranteed to resolve at /pokemon/{id}. Cached (non-blocking) since the
        // count barely ever changes and would otherwise mean an extra PokeAPI call
        // per random-pokemon request.
        this.speciesCount = pokeApiWebClient.get()
                .uri("/pokemon-species?limit=1")
                .retrieve()
                .bodyToMono(SpeciesPage.class)
                .map(SpeciesPage::count)
                .cache(SPECIES_COUNT_CACHE_TTL);
    }

    public Mono<PokemonResponse> getRandomPokemon() {
        return speciesCount
                .map(count -> ThreadLocalRandom.current().nextInt(1, count + 1))
                .flatMap(this::getPokemon);
    }

    private Mono<PokemonResponse> getPokemon(int id) {
        return pokeApiWebClient.get()
                .uri("/pokemon/{id}", id)
                .retrieve()
                .bodyToMono(PokemonPayload.class)
                .map(payload -> payload.toResponse(spriteService.resolveNewestSprite(payload.sprites())));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SpeciesPage(int count) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PokemonPayload(String name, List<Stat> stats, JsonNode sprites) {
        PokemonResponse toResponse(String sprite) {
            return new PokemonResponse(name, toStats(), sprite);
        }

        private PokemonStats toStats() {
            Map<String, Integer> byName = stats.stream()
                    .collect(Collectors.toMap(stat -> stat.stat().name(), Stat::baseStat));
            return new PokemonStats(
                    byName.get("hp"),
                    byName.get("attack"),
                    byName.get("defense"),
                    byName.get("special-attack"),
                    byName.get("special-defense"),
                    byName.get("speed")
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Stat(@JsonProperty("base_stat") int baseStat, StatType stat) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StatType(String name) {
    }
}
