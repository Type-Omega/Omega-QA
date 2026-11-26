# Omega QA

QA kit for the [Type Omega](https://github.com/Type-Omega) mods (TypeOmegaCore, GregTech Nexus Addon, Agronomica), in the spirit of GTNH's Horizon-QA. Written from scratch; no Horizon-QA code is used.

It is a separate mod from [TypeOmega Lib](https://github.com/Type-Omega/TypeOmega-Lib) so players never load it: it belongs in dev runs, CI and test packs only.

## What it has

- **GameTest DSL** (`com.typeomega.qa.dsl`): tick sequences, assertions, fake players, labelled positions, per-test sessions.
- **Rigs** (`qa.gt`): structure, multiblock and machine rigs, temporary recipes, time warp.
- **Sweeps** (`qa.sweeps`): forming from preview, recipe contents, tooltip contract, names in en_us/pt_br/zh_cn, forbidden brand terms, recipe lookup conflicts, progression snapshot export.
- **Reports** (`qa.report`): JUnit XML, JSON and summary, written atomically. Flags: `-Domegaqa.reportDir`, `omegaqa.junitFile`, `omegaqa.jsonFile`, `omegaqa.summaryFile`, `omegaqa.tests`, `omegaqa.exportSnapshot`, `omegaqa.timings`.
- **Perf** (`qa.perf`): tick latency sampler (avg/p95/p99), block entity spike profiler, memory snapshot, `/omegaqa bench multi <machine> <count> <ticks>`.
- **QA wand** (`omegaqa:omega_wand`): select a region, label positions, export a structure plus a `@GameTest` skeleton; failure overlays in the world.
- **Compat bridges** for AE2, EMI and FTB Teams (only active when the mod is loaded) and SPIs other mods implement (`qa.spi`).

## Use it from a mod

```gradle
repositories { maven { url = 'https://type-omega.github.io/maven/' } }
dependencies {
    modCompileOnly("com.typeomega:omega-qa:${omegaqa_version}")   // tests that use the DSL
    modRuntimeOnly("com.typeomega:omega-qa:${omegaqa_version}")   // dev/gametest runs only
}
```

Requires TypeOmega Lib 0.8.0 or newer (`@HeavyTest` / `TestBatches.HEAVY` detection uses TypeOmega Lib 0.8.3+ when present).

### GameTest timings report

To record per-test duration (ticks and real ms), top 20 slowest tests, class/batch totals, and heavy-marked tests (`TestBatches.HEAVY` / `@HeavyTest`), set `omegaqa.timings=<file.json>` in your ModDevGradle `legacyForge` `gameTestServer` run:

```groovy
legacyForge {
    runs {
        gameTestServer {
            type = "gameTestServer"
            sourceSet = sourceSets.main
            systemProperty('forge.enabledGameTestNamespaces', project.mod_id)
            systemProperty('omegaqa.timings', file('build/reports/omegaqa/timings.json').absolutePath)
            // Optional: forward -Domegaqa.* JVM properties passed on the Gradle command line
            System.getProperties().each { k, v ->
                if (k.toString().startsWith('omegaqa.')) {
                    systemProperty(k.toString(), v.toString())
                }
            }
        }
    }
}
```

Then run the full suite (including heavy tests) with:

```bash
./gradlew runGameTestServer -Ptests=all
```

This writes `build/reports/omegaqa/timings.json` and the matching text summary `build/reports/omegaqa/timings.txt`. Without `omegaqa.timings`, nothing is written.

## Build

`./gradlew build` (unit tests), `./gradlew runGameTestServer` (its own GameTests), `./gradlew runData` (lang).

Releases: push a tag `vX.Y.Z`; the organisation Maven picks the jar up.
