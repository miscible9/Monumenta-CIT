# Monumenta CIT Mod

Client-side Fabric prototype for runtime CIT-style item model selection.

This is not an offline converter. Resource packs provide CIT-style matching
rules, and the mod chooses the item model at render time.

OptiFine-style rules live in resource packs under:

```text
assets/<namespace>/optifine/cit/**/*.properties
assets/<namespace>/mcpatcher/cit/**/*.properties
```

Supported properties subset:

```properties
type=item
items=minecraft:crossbow
nbt.display.Name=Ire of the Gods
nbt.Monumenta.PlayerModified.Infusions.Hope.Infuser=a741820c-397f-45d3-90ab-c6b1f79ac9ec
model.crossbow_standby=monumenta_reborn:item/celsian_isles/epic/boss/ire_of_the_gods/ire_of_the_gods
model.crossbow_pulling_0=monumenta_reborn:item/celsian_isles/epic/boss/ire_of_the_gods/ire_of_the_gods_pulling_0
model.crossbow_pulling_1=monumenta_reborn:item/celsian_isles/epic/boss/ire_of_the_gods/ire_of_the_gods_pulling_1
model.crossbow_pulling_2=monumenta_reborn:item/celsian_isles/epic/boss/ire_of_the_gods/ire_of_the_gods_pulling_2
model.crossbow_arrow=monumenta_reborn:item/celsian_isles/epic/boss/ire_of_the_gods/ire_of_the_gods_arrow
model.crossbow_firework=monumenta_reborn:item/celsian_isles/epic/boss/ire_of_the_gods/ire_of_the_gods_firework
```

Model IDs may use the existing model-style form `monumenta_reborn:item/...`.
The mod normalizes that to an item model ID, so a thin wrapper must exist at
`assets/monumenta_reborn/items/...`:

```json
{
  "model": {
    "type": "minecraft:model",
    "model": "monumenta_reborn:item/path/to/model"
  }
}
```

Current prototype support:

- `simple` model replacement
- `crossbow` standby/pulling/arrow/firework states
- exact custom name matching
- `pattern:` and `ipattern:` custom name matching
- Monumenta Hope UUID matching at `Monumenta.PlayerModified.Infusions.Hope.Infuser`
- vanilla item model fallback when no rule matches

Build:

```powershell
& "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat" build
```
