package com.monumenta.reborn.cit.rule

import net.minecraft.util.Identifier

object CitRuleConstants {
    val propertiesRuleRoots = listOf("optifine/cit", "mcpatcher/cit")
    val binaryCompiledIndexId = Identifier.of("monumenta_reborn", "monumenta_cit/rules_index.bin.gz")
    val binaryCompiledIndexMagic = "MCITBIN1".toByteArray(Charsets.US_ASCII)
    val hopePath = "Monumenta.PlayerModified.Infusions.Hope.Infuser"
    val hopeParts = hopePath.split(".")
}
