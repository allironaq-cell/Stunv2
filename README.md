# ShieldStun Combo (Fabric, Minecraft Java 1.21.11, client-only)

Press the combo key near an enemy: the mod switches to your best hotbar **sword**, hits, switches to your
best **axe**, hits (breaking a raised shield), then switches back to your original slot.

> **Fair-play notice:** this is a combat macro. Only use it on singleplayer worlds, your own servers, or
> servers whose rules explicitly allow combat automation. Many servers ban it and their anti-cheat may act on it.

## Keybinds (Options > Controls > Key Binds > "ShieldStun Combo")
| Action | Default |
|---|---|
| Toggle Mod (ON/OFF, action-bar message) | `M` |
| Run Combo (only while ON) | `F` |

## Config: `config/shieldstun.json`
| Option | Default | Meaning |
|---|---|---|
| stepDelayTicks | 0 | Idle ticks between steps (min 0) |
| waitForCooldown | false | Hit only when attack cooldown >= cooldownThreshold |
| cooldownThreshold | 0.9 | Cooldown fraction required |
| forceFastMode | true | Ignore cooldown; hit at stepDelayTicks pace (weaker hits) |
| restoreSlot | true | Return to the original slot afterwards |
| sprintReset | true | Re-send sprint start/stop before the sword hit for sprint knockback |
| criticalHit | false | Only hit while falling (crit). Disables sprintReset (crits can't happen while sprinting) |
| onlyWhenTargetBlocking | false | Only run if the target is blocking |
| repeatWhileHeld | false | Repeat the combo while the key is held |
| showMessages | true | Action-bar messages |
| mergeSwitchAndHit | true | Switch + hit in the same tick (fastest while the hotbar still visibly changes) |
| axeOnlyMode | false | Allow the combo with only an axe |
| targetFovDegrees | 40 | Cone half-angle for the "nearest player" fallback target |

## Build
1. Install JDK 21.
2. Generate the Gradle wrapper once (no wrapper jar is bundled): `gradle wrapper --gradle-version 9.2.1`
   (or just open the folder in IntelliJ IDEA and let it import).
3. `./gradlew build`
4. Jar: `build/libs/shieldstun-1.0.0.jar` (ignore the `-sources` jar). Drop it into `.minecraft/mods` with Fabric API.

If a dependency version fails to resolve, update `gradle.properties` from https://fabricmc.net/develop.
