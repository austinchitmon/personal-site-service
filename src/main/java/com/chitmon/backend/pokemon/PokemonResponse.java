package com.chitmon.backend.pokemon;


public record PokemonResponse(
        String name,
        PokemonStats stats,
        String sprite
) {
}
