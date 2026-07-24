package com.chitmon.backend.pokemon;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PokemonController {

    @GetMapping("/")
    public String getHelloWorld() {
        String hello = "Hello World!";
        return hello;
    }

    @GetMapping("/pokemon")
    public PokemonResponse getPokemon() {
        return new PokemonResponse(
                "Pikachu",
                50
        );
    }
}
