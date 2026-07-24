package com.chitmon.backend.pokemon;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class PokemonController {

    private final PokemonService pokemonService;

    public PokemonController(PokemonService pokemonService) {
        this.pokemonService = pokemonService;
    }

    @GetMapping("/")
    public String getHelloWorld() {
        String hello = "Hello World!";
        return hello;
    }

    @GetMapping("/pokemon/random")
    public Mono<PokemonResponse> getRandomPokemon() {
        return pokemonService.getRandomPokemon();
    }
}
