package com.chitmon.backend.pokemon;

public record PokemonStats(
        int hp,
        int attack,
        int defense,
        int specialAttack,
        int specialDefense,
        int speed
) {
}
