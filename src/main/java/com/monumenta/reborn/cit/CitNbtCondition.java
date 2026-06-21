package com.monumenta.reborn.cit;

import java.util.List;

record CitNbtCondition(String path, List<String> parts, String expected, boolean lorePath) {
    static CitNbtCondition of(String path, String expected) {
        return new CitNbtCondition(path, List.of(path.split("\\.")), expected, path.startsWith("plain.display.Lore"));
    }
}
