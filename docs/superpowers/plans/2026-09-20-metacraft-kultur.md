# Kultur (banner patterns + paintings) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A new METAmods module `mods/metacraft-kultur` (mod id `kultur`) that ships IT's banner/shield patterns (ITK, QMISK, TMEIT free in the loom; Pirkko as a pattern item) and the Draken painting to vanilla clients, generated from one catalogue file plus PNG art.

**Architecture:** `kultur.json` is the catalogue (chapters → patterns, paintings). Datagen (`./gradlew runDatagen`) turns it and `art/kultur/**` into the data-pack JSON (banner patterns, painting variants, tags), the resource-pack assets (normalised textures, item model, lang) under `src/main/generated`. At runtime the mod registers one Polymer item per item-gated pattern, hands its assets to Polymer's pack, and nothing else: banner patterns and painting variants are dynamic registries, synced to vanilla clients by the data files alone.

**Tech Stack:** Minecraft 26.3 (Mojang mappings), Fabric Loader 0.19.5, Fabric API, Polymer 0.18 (core, resource-pack, autohost), Fabric datagen, Fabric game tests, JUnit 5 (`fabric-loader-junit`), java.awt imaging. Java 25.

**Spec:** `docs/superpowers/specs/2026-09-20-metacraft-kultur-design.md`

## Global Constraints

- Module path `mods/metacraft-kultur`, mod id `kultur`, Java package `metacraft.kultur`, version property `kultur_version = 0.1.0`.
- `fabric.mod.json` depends: `fabricloader >=0.19.2`, `minecraft >=26.3-rc.1 <26.4`, `java >=25`, `fabric-api *`, `polymer-core *`, `polymer-resource-pack *`.
- Registry ids: `kultur:<pattern>` and `kultur:<painting>`; asset ids the same. Pattern items: `kultur:<pattern>_banner_pattern`.
- Lang keys: `block.kultur.banner.<p>.<color>`, `item.kultur.<p>_banner_pattern`, `painting.kultur.<q>.title`, `painting.kultur.<q>.author`. English only.
- Catalogue ids match `[a-z0-9_]+` and are unique per kind; `item` defaults to false.
- Banner textures are 64×64 or an integer multiple; datagen keeps only the front face (20×40 at (1,1), scaled) and copies it onto the back face (at (22,1)). Shield textures keep only the face (12×22 at (1,1), scaled). A shield with no file is the banner's front face at half size in the 10×20 region at (2,2).
- Painting PNGs are exactly `16·width × 16·height`.
- No loot tables, no recipes, no mixins, no config, no Data/Media content.
- **Do not `git add` or commit.** Edvin stages and commits himself. Each task ends by telling him it is done and what to look at.
- House style (see `mods/ovvar`, `mods/moredyes`): tabs, Javadoc that says *why*, `final` classes with private constructors for helpers, `LOGGER` tagged `[kultur]`.
- Datagen writes into `src/main/generated` (committed). Bash is the shell for the commands below; from PowerShell set `$env:JAVA_TOOL_OPTIONS` instead of the inline prefix.

---

## File map

| file | responsibility |
| --- | --- |
| `settings.gradle` (modify) | include the module |
| `gradle.properties` (modify) | `kultur_version` |
| `mods/metacraft-kultur/build.gradle` | version, datagen, Polymer deps, generated-assets guard |
| `mods/metacraft-kultur/src/main/resources/fabric.mod.json` | mod metadata, entrypoints |
| `.../resources/kultur.json` | the catalogue |
| `.../resources/art/kultur/it/{banner,shield,item,painting}/*.png` | source art |
| `.../java/metacraft/kultur/Kultur.java` | entrypoint |
| `.../java/metacraft/kultur/catalogue/Catalogue.java` | catalogue records, codec, load, validation |
| `.../java/metacraft/kultur/content/PatternItem.java` | the Polymer pattern item |
| `.../java/metacraft/kultur/content/ModContent.java` | registers items from the catalogue |
| `.../java/metacraft/kultur/datagen/Masks.java` | banner/shield texture normalisation and derivation (pure imaging) |
| `.../java/metacraft/kultur/datagen/J.java` | JSON literal helpers |
| `.../java/metacraft/kultur/datagen/Writes.java` | cached JSON/PNG writer |
| `.../java/metacraft/kultur/datagen/GeneratedAssets.java` | the data provider |
| `.../java/metacraft/kultur/datagen/KulturDataGenerator.java` | datagen entrypoint |
| `.../java/metacraft/kultur/gametest/KulturGameTests.java` | server game tests |
| `.../src/test/java/metacraft/kultur/CatalogueTest.java` | unit tests |
| `.../src/test/java/metacraft/kultur/MasksTest.java` | unit tests |
| `.../src/gametest/java/metacraft/kultur/clienttest/KulturClientTests.java` (+ mixins, `fabric.mod.json`) | client screenshot test |
| `mods/metacraft-kultur/README.md` | the contributor guide |

---

### Task 1: Module scaffold and the catalogue

**Files:**
- Modify: `settings.gradle` (after `include "mods:ovvar"`)
- Modify: `gradle.properties` (after `ovvar_version = 0.1.0`)
- Create: `mods/metacraft-kultur/build.gradle`
- Create: `mods/metacraft-kultur/src/main/resources/fabric.mod.json`
- Create: `mods/metacraft-kultur/src/main/resources/kultur.json`
- Create: `mods/metacraft-kultur/src/main/java/metacraft/kultur/Kultur.java`
- Create: `mods/metacraft-kultur/src/main/java/metacraft/kultur/catalogue/Catalogue.java`
- Test: `mods/metacraft-kultur/src/test/java/metacraft/kultur/CatalogueTest.java`

**Interfaces:**
- Produces: `Catalogue` record with `chapters()`, nested records `Chapter(id, name, patterns, paintings)`, `Pattern(id, name, item)`, `Painting(id, title, author, width, height)`, `Owned<T>(chapter, value)`; statics `Catalogue.load()`, `Catalogue.parse(JsonElement)`; instance `patterns()` and `paintings()` returning `List<Owned<Pattern>>` / `List<Owned<Painting>>`; `Kultur.MOD_ID`, `Kultur.LOGGER`.

- [ ] **Step 1: Wire the module into the build**

`settings.gradle`, after `include "mods:ovvar"`:

```groovy
include "mods:metacraft-kultur"
```

`gradle.properties`, after `ovvar_version = 0.1.0`:

```properties
	kultur_version = 0.1.0
```

`mods/metacraft-kultur/build.gradle`:

```groovy
// Kultur — chapter culture (banner and shield patterns, paintings; later drinks) for vanilla clients (Polymer).
// See README.md in this module.
version = project.kultur_version

// Every catalogue-derived file (banner patterns, painting variants, tags, textures, item model, lang)
// comes from src/main/resources/kultur.json and art/kultur via data generation: ./gradlew runDatagen.
setupRunDatagen("kultur")

addPolymerDependency()
dependencies {
	implementation("eu.pb4:polymer-resource-pack:${project.polymer_version}")
	implementation("eu.pb4:polymer-autohost:${project.polymer_version}")
}
```

(The generated-assets guard on `processResources` is added in Task 3, once there is something to guard.)

- [ ] **Step 2: Mod metadata**

`mods/metacraft-kultur/src/main/resources/fabric.mod.json`:

```json
{
	"schemaVersion": 1,
	"id": "kultur",
	"version": "${version}",
	"name": "Kultur",
	"description": "Chapter culture for METAcraft: banner and shield patterns and paintings for vanilla clients, via Polymer. Players only need the auto-served resource pack.",
	"authors": [
		"Edvin"
	],
	"contact": {
		"sources": "${git_repo}",
		"homepage": "${website}",
		"issues": "${issues}"
	},
	"license": "MIT",
	"environment": "*",
	"entrypoints": {
		"main": [
			"metacraft.kultur.Kultur"
		],
		"fabric-gametest": [
			"metacraft.kultur.gametest.KulturGameTests"
		],
		"fabric-datagen": [
			"metacraft.kultur.datagen.KulturDataGenerator"
		]
	},
	"depends": {
		"fabricloader": ">=0.19.2",
		"minecraft": ">=26.3-rc.1 <26.4",
		"java": ">=25",
		"fabric-api": "*",
		"polymer-core": "*",
		"polymer-resource-pack": "*"
	}
}
```

The gametest and datagen classes named here arrive in Tasks 3 and 4; Fabric only resolves entrypoints when that entrypoint kind runs, so the main entrypoint and the unit tests work before then.

- [ ] **Step 3: The catalogue file**

`mods/metacraft-kultur/src/main/resources/kultur.json`:

```json
{
	"chapters": [
		{
			"id": "it",
			"name": "IT",
			"patterns": [
				{ "id": "itk", "name": "ITK" },
				{ "id": "qmisk", "name": "QMISK" },
				{ "id": "tmeit", "name": "TMEIT" },
				{ "id": "pirkko", "name": "Pirkko", "item": true }
			],
			"paintings": [
				{ "id": "draken", "title": "The Guardian of Kistan", "author": "Emelie Stark", "width": 3, "height": 4 }
			]
		},
		{ "id": "data", "name": "Data", "patterns": [], "paintings": [] },
		{ "id": "media", "name": "Media", "patterns": [], "paintings": [] }
	]
}
```

- [ ] **Step 4: Write the failing catalogue tests**

`mods/metacraft-kultur/src/test/java/metacraft/kultur/CatalogueTest.java`:

```java
package metacraft.kultur;

import com.google.gson.JsonParser;
import metacraft.kultur.catalogue.Catalogue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CatalogueTest {
	@Test
	public void shippedCatalogueLoads() {
		Catalogue c = Catalogue.load();
		assertEquals(List.of("it", "data", "media"), c.chapters().stream().map(Catalogue.Chapter::id).toList());
		var patterns = c.patterns();
		assertEquals(List.of("itk", "qmisk", "tmeit", "pirkko"), patterns.stream().map(o -> o.value().id()).toList());
		assertTrue(patterns.stream().allMatch(o -> o.chapter().id().equals("it")));
		assertFalse(patterns.get(0).value().item(), "itk is free in the loom");
		assertTrue(patterns.get(3).value().item(), "pirkko needs its item");
		var paintings = c.paintings();
		assertEquals(1, paintings.size());
		Catalogue.Painting draken = paintings.getFirst().value();
		assertEquals("draken", draken.id());
		assertEquals("The Guardian of Kistan", draken.title());
		assertEquals("Emelie Stark", draken.author());
		assertEquals(3, draken.width());
		assertEquals(4, draken.height());
	}

	@Test
	public void itemDefaultsToFalseAndListsToEmpty() {
		Catalogue c = Catalogue.parse(JsonParser.parseString("""
				{"chapters": [{"id": "x", "name": "X", "patterns": [{"id": "a", "name": "A"}]}]}"""));
		assertFalse(c.patterns().getFirst().value().item());
		assertTrue(c.paintings().isEmpty());
	}

	@Test
	public void duplicateIdsAreRefused() {
		var e = assertThrows(IllegalStateException.class, () -> Catalogue.parse(JsonParser.parseString("""
				{"chapters": [
					{"id": "x", "name": "X", "patterns": [{"id": "a", "name": "A"}]},
					{"id": "y", "name": "Y", "patterns": [{"id": "a", "name": "A again"}]}]}""")));
		assertTrue(e.getMessage().contains("a"), e.getMessage());
	}

	@Test
	public void badIdsAreRefused() {
		assertThrows(IllegalStateException.class, () -> Catalogue.parse(JsonParser.parseString("""
				{"chapters": [{"id": "x", "name": "X", "patterns": [{"id": "Släggan", "name": "S"}]}]}""")));
		assertThrows(IllegalStateException.class, () -> Catalogue.parse(JsonParser.parseString("""
				{"chapters": [{"id": "x", "name": "X", "paintings": [{"id": "p", "title": "T", "author": "A", "width": 0, "height": 1}]}]}""")));
	}
}
```

- [ ] **Step 5: Run the tests to see them fail**

Run: `./gradlew :mods:metacraft-kultur:test --tests 'metacraft.kultur.CatalogueTest'`
Expected: compilation failure, `Catalogue` does not exist.

- [ ] **Step 6: The entrypoint and the catalogue**

`mods/metacraft-kultur/src/main/java/metacraft/kultur/Kultur.java` (the item registration and the pack lines come in Task 4; keep this minimal now):

```java
package metacraft.kultur;

import metacraft.kultur.catalogue.Catalogue;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kultur: chapter culture for vanilla clients via Polymer — banner and shield patterns and
 * paintings now, drinks later. Everything the client sees is in the data files and the pack, so
 * the mod's own job is small: read the catalogue, register the pattern items, hand Polymer the assets.
 */
public class Kultur implements ModInitializer {
	public static final String MOD_ID = "kultur";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Catalogue catalogue = Catalogue.load();
		LOGGER.info("[{}] {} chapter(s), {} pattern(s), {} painting(s)", MOD_ID,
				catalogue.chapters().size(), catalogue.patterns().size(), catalogue.paintings().size());
	}
}
```

`mods/metacraft-kultur/src/main/java/metacraft/kultur/catalogue/Catalogue.java`:

```java
package metacraft.kultur.catalogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The one list everything else is derived from: {@code kultur.json} in the mod's resources. Datagen
 * reads it to write the data and assets; the server reads it to register the pattern items. A
 * chapter is a folder of art and a group in the catalogue, nothing more — a Data or Media student
 * adds their entries here and their PNGs under {@code art/kultur/<chapter>/}.
 */
public record Catalogue(List<Chapter> chapters) {
	private static final java.util.regex.Pattern ID = java.util.regex.Pattern.compile("[a-z0-9_]+");

	/** A banner pattern; {@code item} means the loom wants a pattern item for it (the vanilla globe / flower mechanic). */
	public record Pattern(String id, String name, boolean item) {
		public static final Codec<Pattern> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("id").forGetter(Pattern::id),
				Codec.STRING.fieldOf("name").forGetter(Pattern::name),
				Codec.BOOL.optionalFieldOf("item", false).forGetter(Pattern::item)
		).apply(i, Pattern::new));

		/** The pattern item's registry path, when {@link #item}. */
		public String itemPath() {
			return id + "_banner_pattern";
		}
	}

	/** A painting variant; {@code width}/{@code height} in blocks, the PNG is 16 px per block. */
	public record Painting(String id, String title, String author, int width, int height) {
		public static final Codec<Painting> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("id").forGetter(Painting::id),
				Codec.STRING.fieldOf("title").forGetter(Painting::title),
				Codec.STRING.fieldOf("author").forGetter(Painting::author),
				Codec.INT.fieldOf("width").forGetter(Painting::width),
				Codec.INT.fieldOf("height").forGetter(Painting::height)
		).apply(i, Painting::new));
	}

	public record Chapter(String id, String name, List<Pattern> patterns, List<Painting> paintings) {
		public static final Codec<Chapter> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("id").forGetter(Chapter::id),
				Codec.STRING.fieldOf("name").forGetter(Chapter::name),
				Pattern.CODEC.listOf().optionalFieldOf("patterns", List.of()).forGetter(Chapter::patterns),
				Painting.CODEC.listOf().optionalFieldOf("paintings", List.of()).forGetter(Chapter::paintings)
		).apply(i, Chapter::new));
	}

	/** An entry with the chapter it belongs to — the chapter names the art folder. */
	public record Owned<T>(Chapter chapter, T value) {}

	public static final Codec<Catalogue> CODEC = RecordCodecBuilder.create(i -> i.group(
			Chapter.CODEC.listOf().fieldOf("chapters").forGetter(Catalogue::chapters)
	).apply(i, Catalogue::new));

	/** The shipped catalogue, validated. */
	public static Catalogue load() {
		try (InputStream in = Catalogue.class.getResourceAsStream("/kultur.json")) {
			if (in == null) throw new IllegalStateException("kultur.json is missing from the jar");
			return parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Parses and validates; every complaint names the entry so a contributor can find it. */
	public static Catalogue parse(JsonElement json) {
		Catalogue c = CODEC.parse(JsonOps.INSTANCE, json)
				.getOrThrow(msg -> new IllegalStateException("kultur.json: " + msg));
		Set<String> chapterIds = new HashSet<>(), patternIds = new HashSet<>(), paintingIds = new HashSet<>();
		for (Chapter chapter : c.chapters) {
			id(chapter.id, "chapter");
			if (!chapterIds.add(chapter.id)) throw new IllegalStateException("kultur.json: chapter id '" + chapter.id + "' twice");
			for (Pattern p : chapter.patterns) {
				id(p.id, "pattern in chapter " + chapter.id);
				if (!patternIds.add(p.id)) throw new IllegalStateException("kultur.json: pattern id '" + p.id + "' twice");
			}
			for (Painting q : chapter.paintings) {
				id(q.id, "painting in chapter " + chapter.id);
				if (!paintingIds.add(q.id)) throw new IllegalStateException("kultur.json: painting id '" + q.id + "' twice");
				if (q.width < 1 || q.height < 1) throw new IllegalStateException("kultur.json: painting '" + q.id + "' is " + q.width + "×" + q.height + " blocks");
			}
		}
		return c;
	}

	private static void id(String id, String what) {
		if (!ID.matcher(id).matches()) throw new IllegalStateException("kultur.json: " + what + " id '" + id + "' is not [a-z0-9_]+");
	}

	public List<Owned<Pattern>> patterns() {
		List<Owned<Pattern>> out = new ArrayList<>();
		for (Chapter chapter : chapters) for (Pattern p : chapter.patterns) out.add(new Owned<>(chapter, p));
		return out;
	}

	public List<Owned<Painting>> paintings() {
		List<Owned<Painting>> out = new ArrayList<>();
		for (Chapter chapter : chapters) for (Painting q : chapter.paintings) out.add(new Owned<>(chapter, q));
		return out;
	}
}
```

- [ ] **Step 7: Run the tests to see them pass**

Run: `./gradlew :mods:metacraft-kultur:test --tests 'metacraft.kultur.CatalogueTest'`
Expected: BUILD SUCCESSFUL, 4 tests passed. If Gradle complains about the missing `src/main/generated` directory, ignore it here: the guard is not in place yet and the folder appears in Task 3.

- [ ] **Step 8: Checkpoint**

Tell Edvin Task 1 is done and which files to look at. Do not commit.

---

### Task 2: Art and the mask normaliser

**Files:**
- Create: `mods/metacraft-kultur/src/main/resources/art/kultur/it/banner/{itk,qmisk,tmeit,pirkko}.png`
- Create: `mods/metacraft-kultur/src/main/resources/art/kultur/it/shield/{itk,qmisk,tmeit,pirkko}.png`
- Create: `mods/metacraft-kultur/src/main/resources/art/kultur/it/item/pirkko.png`
- Create: `mods/metacraft-kultur/src/main/resources/art/kultur/it/painting/draken.png`
- Create: `mods/metacraft-kultur/src/main/java/metacraft/kultur/datagen/Masks.java`
- Test: `mods/metacraft-kultur/src/test/java/metacraft/kultur/MasksTest.java`

**Interfaces:**
- Produces: `Masks.scale(BufferedImage) -> int`, `Masks.banner(BufferedImage)`, `Masks.shield(BufferedImage)`, `Masks.shieldFromBanner(BufferedImage)`, `Masks.read(InputStream)`, `Masks.png(BufferedImage) -> byte[]`, and the region constants `BANNER_W/H`, `BANNER_FRONT_X/Y`, `BANNER_BACK_X`, `SHIELD_FACE_X/Y/W/H`, `SHIELD_X/Y/W/H`.

- [ ] **Step 1: Fetch PolymITer's art**

From the repo root, in bash (needs `gh` logged in, which it is on this machine):

```bash
A=mods/metacraft-kultur/src/main/resources/art/kultur/it
mkdir -p $A/banner $A/shield $A/item $A/painting
R=repos/Froosty11/PolymITer/contents/src/main/resources/assets/polymiter/textures
for p in itk qmisk tmeit pirkko; do
  gh api "$R/entity/banner/$p.png" --jq .content | base64 -d > $A/banner/$p.png
  gh api "$R/entity/shield/$p.png" --jq .content | base64 -d > $A/shield/$p.png
done
gh api "$R/item/pirkko_banner_pattern.png" --jq .content | base64 -d > $A/item/pirkko.png
gh api "$R/painting/draken.png" --jq .content | base64 -d > $A/painting/draken.png
python -c "
import struct,glob
for f in sorted(glob.glob('$A/*/*.png')):
    d=open(f,'rb').read(); print(f, struct.unpack('>II', d[16:24]))"
```

Expected sizes: every banner and shield `(128, 128)`, `item/pirkko.png` `(16, 16)`, `painting/draken.png` `(48, 64)`.

Known facts about this art, for the eyes-on check later: the banner masks are drawn on the front face only (at 128×128 that is x 2..41, y 2..81); the back face is empty, so datagen's copy is what makes the back of a banner show the pattern. `banner/tmeit.png` and `shield/itk.png` each carry one stray pixel outside the face, which normalisation clears. `shield/pirkko.png`'s ghost runs 1 px past the face's right edge at 1× and gets clipped there; it is what shipped in PolymITer, so keep it.

- [ ] **Step 2: Write the failing mask tests**

`mods/metacraft-kultur/src/test/java/metacraft/kultur/MasksTest.java`:

```java
package metacraft.kultur;

import metacraft.kultur.datagen.Masks;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MasksTest {
	private static BufferedImage art(String path) throws Exception {
		try (InputStream in = MasksTest.class.getResourceAsStream("/art/kultur/" + path)) {
			if (in == null) throw new AssertionError("no art at " + path);
			return Masks.read(in);
		}
	}

	private static int alphaOutside(BufferedImage img, int x0, int y0, int w, int h) {
		int n = 0;
		for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
			boolean inside = x >= x0 && x < x0 + w && y >= y0 && y < y0 + h;
			if (!inside && (img.getRGB(x, y) >>> 24) != 0) n++;
		}
		return n;
	}

	private static int opaque(BufferedImage img, int x0, int y0, int w, int h) {
		int n = 0;
		for (int y = y0; y < y0 + h; y++) for (int x = x0; x < x0 + w; x++) if ((img.getRGB(x, y) >>> 24) != 0) n++;
		return n;
	}

	@Test
	public void scaleIsTheMultipleOf64() {
		assertEquals(1, Masks.scale(new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)));
		assertEquals(2, Masks.scale(new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB)));
		assertThrows(IllegalArgumentException.class, () -> Masks.scale(new BufferedImage(64, 128, BufferedImage.TYPE_INT_ARGB)));
		assertThrows(IllegalArgumentException.class, () -> Masks.scale(new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)));
	}

	/** The front face survives, the back face is its copy, and nothing else remains (tmeit's stray pixel goes). */
	@Test
	public void bannerKeepsTheFrontAndCopiesItToTheBack() throws Exception {
		BufferedImage src = art("it/banner/tmeit.png");
		BufferedImage out = Masks.banner(src);
		int k = 2;
		assertEquals(128, out.getWidth());
		int front = opaque(out, Masks.BANNER_FRONT_X * k, Masks.BANNER_FRONT_Y * k, Masks.BANNER_W * k, Masks.BANNER_H * k);
		assertTrue(front > 200, "front face is nearly empty: " + front);
		assertEquals(front, opaque(src, Masks.BANNER_FRONT_X * k, Masks.BANNER_FRONT_Y * k, Masks.BANNER_W * k, Masks.BANNER_H * k), "front face changed");
		for (int y = 0; y < Masks.BANNER_H * k; y++) for (int x = 0; x < Masks.BANNER_W * k; x++) {
			int f = out.getRGB(Masks.BANNER_FRONT_X * k + x, Masks.BANNER_FRONT_Y * k + y);
			int b = out.getRGB(Masks.BANNER_BACK_X * k + x, Masks.BANNER_FRONT_Y * k + y);
			assertEquals(f, b, "back differs from front at " + x + "," + y);
		}
		// Only the two faces carry alpha.
		int outside = 0;
		for (int y = 0; y < out.getHeight(); y++) for (int x = 0; x < out.getWidth(); x++) {
			boolean inFront = x >= Masks.BANNER_FRONT_X * k && x < (Masks.BANNER_FRONT_X + Masks.BANNER_W) * k && y >= Masks.BANNER_FRONT_Y * k && y < (Masks.BANNER_FRONT_Y + Masks.BANNER_H) * k;
			boolean inBack = x >= Masks.BANNER_BACK_X * k && x < (Masks.BANNER_BACK_X + Masks.BANNER_W) * k && y >= Masks.BANNER_FRONT_Y * k && y < (Masks.BANNER_FRONT_Y + Masks.BANNER_H) * k;
			if (!inFront && !inBack && (out.getRGB(x, y) >>> 24) != 0) outside++;
		}
		assertEquals(0, outside, "alpha outside the faces");
	}

	/** A hand-drawn shield keeps only its face (itk's stray pixel goes). */
	@Test
	public void shieldKeepsOnlyTheFace() throws Exception {
		BufferedImage out = Masks.shield(art("it/shield/itk.png"));
		int k = 2;
		assertEquals(0, alphaOutside(out, Masks.SHIELD_FACE_X * k, Masks.SHIELD_FACE_Y * k, Masks.SHIELD_FACE_W * k, Masks.SHIELD_FACE_H * k));
		assertTrue(opaque(out, Masks.SHIELD_FACE_X * k, Masks.SHIELD_FACE_Y * k, Masks.SHIELD_FACE_W * k, Masks.SHIELD_FACE_H * k) > 100);
	}

	/** Derived: the banner's front face at half size lands in the shield's pattern region, and nowhere else. */
	@Test
	public void shieldFromBannerIsTheFrontFaceHalved() throws Exception {
		BufferedImage banner = art("it/banner/itk.png");
		BufferedImage out = Masks.shieldFromBanner(banner);
		int k = 2;
		assertEquals(128, out.getWidth());
		assertEquals(0, alphaOutside(out, Masks.SHIELD_X * k, Masks.SHIELD_Y * k, Masks.SHIELD_W * k, Masks.SHIELD_H * k));
		int got = opaque(out, Masks.SHIELD_X * k, Masks.SHIELD_Y * k, Masks.SHIELD_W * k, Masks.SHIELD_H * k);
		int src = opaque(banner, Masks.BANNER_FRONT_X * k, Masks.BANNER_FRONT_Y * k, Masks.BANNER_W * k, Masks.BANNER_H * k);
		// Max-alpha over 2×2 blocks: between a quarter of the source texels and all of them.
		assertTrue(got >= src / 4 && got <= src, got + " opaque texels from " + src);
		// A single opaque source texel is enough to light its block.
		BufferedImage one = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		one.setRGB(Masks.BANNER_FRONT_X + 5, Masks.BANNER_FRONT_Y + 7, 0xFF102030);
		BufferedImage d = Masks.shieldFromBanner(one);
		assertEquals(0xFF102030, d.getRGB(Masks.SHIELD_X + 2, Masks.SHIELD_Y + 3));
		assertEquals(1, opaque(d, 0, 0, 64, 64));
	}

	@Test
	public void emptyInGivesEmptyOut() {
		BufferedImage blank = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		assertEquals(0, opaque(Masks.banner(blank), 0, 0, 64, 64));
		assertEquals(0, opaque(Masks.shield(blank), 0, 0, 64, 64));
		assertEquals(0, opaque(Masks.shieldFromBanner(blank), 0, 0, 64, 64));
	}

	@Test
	public void pngRoundTrips() throws Exception {
		BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		img.setRGB(3, 4, 0x80FF0000);
		BufferedImage back = Masks.read(new java.io.ByteArrayInputStream(Masks.png(img)));
		assertEquals(0x80FF0000, back.getRGB(3, 4));
	}
}
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew :mods:metacraft-kultur:test --tests 'metacraft.kultur.MasksTest'`
Expected: compilation failure, `Masks` does not exist.

- [ ] **Step 4: Write `Masks`**

`mods/metacraft-kultur/src/main/java/metacraft/kultur/datagen/Masks.java`:

```java
package metacraft.kultur.datagen;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Banner and shield pattern textures in vanilla's layout, from art that need not be tidy.
 *
 * <p>Vanilla's {@code entity/banner/<pattern>.png} is 64×64: the flag is a 20×40×1 box with its
 * UV at (0,0), so the front face is the 20×40 at (1,1) and the back face the 20×40 at (22,1), and
 * every vanilla pattern paints both (the back of a banner shows the pattern). PolymITer's art
 * painted the front only, so the back is filled here by copying the front — what vanilla's own
 * files do. {@code entity/shield/<pattern>.png} is 64×64 too: the plate is 12×22×1 at (0,0), its
 * face the 12×22 at (1,1), and vanilla masks stay inside the 10×20 at (2,2) with a texel of margin.
 * A hand-drawn shield keeps its whole face; a shield derived from a banner is the front face at
 * half size, which is exactly the 10×20 region. Everything outside the faces is cleared, which is
 * also what drops the odd stray pixel in a source file.
 *
 * <p>Textures may be any integer multiple of 64×64 (PolymITer's are 128×128); every region scales
 * with the file. Pure imaging, no Minecraft classes, so a unit test can hold it.
 */
public final class Masks {
	public static final int BANNER_W = 20, BANNER_H = 40, BANNER_FRONT_X = 1, BANNER_FRONT_Y = 1, BANNER_BACK_X = 22;
	public static final int SHIELD_FACE_X = 1, SHIELD_FACE_Y = 1, SHIELD_FACE_W = 12, SHIELD_FACE_H = 22;
	public static final int SHIELD_X = 2, SHIELD_Y = 2, SHIELD_W = 10, SHIELD_H = 20;

	private Masks() {}

	/** The texture's multiple of 64; a size that is not square or not a multiple of 64 is refused. */
	public static int scale(BufferedImage img) {
		int w = img.getWidth(), h = img.getHeight();
		if (w != h || w % 64 != 0 || w == 0) {
			throw new IllegalArgumentException("texture is " + w + "×" + h + ", wanted 64×64 or an integer multiple of it");
		}
		return w / 64;
	}

	/** The front face as drawn, copied onto the back face, nothing anywhere else. */
	public static BufferedImage banner(BufferedImage src) {
		int k = scale(src);
		BufferedImage out = blank(64 * k);
		copy(src, BANNER_FRONT_X * k, BANNER_FRONT_Y * k, out, BANNER_FRONT_X * k, BANNER_FRONT_Y * k, BANNER_W * k, BANNER_H * k);
		copy(src, BANNER_FRONT_X * k, BANNER_FRONT_Y * k, out, BANNER_BACK_X * k, BANNER_FRONT_Y * k, BANNER_W * k, BANNER_H * k);
		return out;
	}

	/** The shield's face as drawn, nothing anywhere else. */
	public static BufferedImage shield(BufferedImage src) {
		int k = scale(src);
		BufferedImage out = blank(64 * k);
		copy(src, SHIELD_FACE_X * k, SHIELD_FACE_Y * k, out, SHIELD_FACE_X * k, SHIELD_FACE_Y * k, SHIELD_FACE_W * k, SHIELD_FACE_H * k);
		return out;
	}

	/**
	 * The banner's front face at half size in the shield's pattern region: alpha is the max of each
	 * 2×2 block (a thin line stays a line), colour the mean of the block's opaque texels.
	 */
	public static BufferedImage shieldFromBanner(BufferedImage banner) {
		int k = scale(banner);
		BufferedImage out = blank(64 * k);
		for (int y = 0; y < SHIELD_H * k; y++) {
			for (int x = 0; x < SHIELD_W * k; x++) {
				int a = 0, r = 0, g = 0, b = 0, n = 0;
				for (int dy = 0; dy < 2; dy++) {
					for (int dx = 0; dx < 2; dx++) {
						int p = banner.getRGB(BANNER_FRONT_X * k + 2 * x + dx, BANNER_FRONT_Y * k + 2 * y + dy);
						int pa = p >>> 24;
						if (pa == 0) continue;
						a = Math.max(a, pa);
						r += (p >> 16) & 0xFF;
						g += (p >> 8) & 0xFF;
						b += p & 0xFF;
						n++;
					}
				}
				if (n > 0) out.setRGB(SHIELD_X * k + x, SHIELD_Y * k + y, (a << 24) | ((r / n) << 16) | ((g / n) << 8) | (b / n));
			}
		}
		return out;
	}

	public static BufferedImage read(InputStream in) {
		try {
			BufferedImage img = ImageIO.read(in);
			if (img == null) throw new IOException("not a PNG");
			BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
			argb.getGraphics().drawImage(img, 0, 0, null);
			return argb;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static byte[] png(BufferedImage img) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(img, "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static BufferedImage blank(int size) {
		return new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
	}

	private static void copy(BufferedImage src, int sx, int sy, BufferedImage dst, int dx, int dy, int w, int h) {
		for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) dst.setRGB(dx + x, dy + y, src.getRGB(sx + x, sy + y));
	}
}
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :mods:metacraft-kultur:test --tests 'metacraft.kultur.MasksTest'`
Expected: BUILD SUCCESSFUL, 6 tests passed.

- [ ] **Step 6: Checkpoint**

Tell Edvin Task 2 is done: the art is in `art/kultur/it/`, `Masks` is tested. Do not commit.

---

### Task 3: Datagen

**Files:**
- Create: `mods/metacraft-kultur/src/main/java/metacraft/kultur/datagen/J.java`
- Create: `mods/metacraft-kultur/src/main/java/metacraft/kultur/datagen/Writes.java`
- Create: `mods/metacraft-kultur/src/main/java/metacraft/kultur/datagen/GeneratedAssets.java`
- Create: `mods/metacraft-kultur/src/main/java/metacraft/kultur/datagen/KulturDataGenerator.java`
- Modify: `mods/metacraft-kultur/build.gradle` (the guard)
- Generated (committed): `mods/metacraft-kultur/src/main/generated/**`

**Interfaces:**
- Consumes: `Catalogue`, `Masks` from Tasks 1–2.
- Produces: the generated files listed in the spec; the lang keys and asset paths Task 4's runtime and tests rely on:
  - `assets/kultur/textures/entity/banner/<p>.png`, `assets/kultur/textures/entity/shield/<p>.png`
  - `assets/kultur/textures/painting/<q>.png`
  - `assets/kultur/textures/item/<p>_banner_pattern.png`, `assets/kultur/models/item/<p>_banner_pattern.json`, `assets/kultur/items/<p>_banner_pattern.json`
  - `assets/kultur/lang/en_us.json`
  - `data/kultur/banner_pattern/<p>.json`, `data/kultur/painting_variant/<q>.json`
  - `data/minecraft/tags/banner_pattern/no_item_required.json`, `data/kultur/tags/banner_pattern/pattern_item/<p>.json`, `data/minecraft/tags/painting_variant/placeable.json`

- [ ] **Step 1: The two small helpers**

`J.java` — the same tiny JSON helpers ovvar and moredyes carry (a copy on purpose: each module builds alone):

```java
package metacraft.kultur.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** Tiny JSON literal helpers so the generator reads like the data it emits. */
final class J {
	private J() {}

	/** {@code obj("k", v, "k2", v2, ...)}; values may be JsonElement, String, Number, Boolean, or null (skipped). */
	static JsonObject obj(Object... kv) {
		JsonObject o = new JsonObject();
		for (int i = 0; i < kv.length; i += 2) {
			Object v = kv[i + 1];
			if (v != null) o.add((String) kv[i], el(v));
		}
		return o;
	}

	static JsonArray strings(Iterable<String> items) {
		JsonArray a = new JsonArray();
		for (String s : items) a.add(s);
		return a;
	}

	static JsonElement el(Object v) {
		if (v instanceof JsonElement e) return e;
		if (v instanceof String s) return new JsonPrimitive(s);
		if (v instanceof Number n) return new JsonPrimitive(n);
		if (v instanceof Boolean b) return new JsonPrimitive(b);
		throw new IllegalArgumentException("not JSON: " + v);
	}
}
```

`Writes.java`:

```java
package metacraft.kultur.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonElement;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The two ways anything is written into {@code src/main/generated} — a JSON document and a PNG —
 * through the run's {@link CachedOutput}, which leaves a file whose bytes have not changed alone.
 */
final class Writes {
	private final CachedOutput out;
	private final List<CompletableFuture<?>> pending = new ArrayList<>();

	Writes(CachedOutput out) {
		this.out = out;
	}

	/** A JSON document, written stably — keys sorted, so the same tree is always the same bytes. */
	void json(Path path, JsonElement element) {
		pending.add(DataProvider.saveStable(out, element, path));
	}

	void png(Path path, byte[] data) {
		pending.add(CompletableFuture.runAsync(() -> {
			try {
				out.writeIfNeeded(path, data, Hashing.sha1().hashBytes(data));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}));
	}

	CompletableFuture<?> allOf() {
		return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
	}
}
```

- [ ] **Step 2: The provider**

`GeneratedAssets.java`:

```java
package metacraft.kultur.datagen;

import com.google.gson.JsonObject;
import metacraft.kultur.Kultur;
import metacraft.kultur.catalogue.Catalogue;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static metacraft.kultur.datagen.J.obj;
import static metacraft.kultur.datagen.J.strings;

/**
 * Everything derived from the catalogue and the art: the banner pattern and painting variant
 * definitions, the tags that put free patterns in every loom and paintings on walls, the textures
 * in vanilla's layout ({@link Masks}), the pattern items' models, and the lang file — sixteen
 * colour names per pattern, which is the line count that makes generating it worth it.
 *
 * <p>Fails loudly, naming the entry, when art is missing or the wrong size: a contributor sees the
 * problem at {@code runDatagen}, not as an invisible pattern on a server.
 */
public final class GeneratedAssets implements DataProvider {
	private static final String MOD = Kultur.MOD_ID;
	/** Vanilla's sixteen dyes, in DyeColor order, with the names its own lang file uses. */
	static final String[][] DYES = {
			{"white", "White"}, {"orange", "Orange"}, {"magenta", "Magenta"}, {"light_blue", "Light Blue"},
			{"yellow", "Yellow"}, {"lime", "Lime"}, {"pink", "Pink"}, {"gray", "Gray"},
			{"light_gray", "Light Gray"}, {"cyan", "Cyan"}, {"purple", "Purple"}, {"blue", "Blue"},
			{"brown", "Brown"}, {"green", "Green"}, {"red", "Red"}, {"black", "Black"}};

	private final Path assets, data, minecraftData;
	private Writes files;

	public GeneratedAssets(FabricPackOutput output) {
		this.assets = output.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve(MOD);
		this.data = output.getOutputFolder(PackOutput.Target.DATA_PACK).resolve(MOD);
		this.minecraftData = output.getOutputFolder(PackOutput.Target.DATA_PACK).resolve("minecraft");
	}

	@Override
	public String getName() {
		return "Kultur catalogue-derived assets";
	}

	@Override
	public CompletableFuture<?> run(CachedOutput output) {
		this.files = new Writes(output);
		Catalogue catalogue = Catalogue.load();
		Map<String, String> lang = new LinkedHashMap<>();
		List<String> free = new ArrayList<>(), placeable = new ArrayList<>();

		for (Catalogue.Owned<Catalogue.Pattern> owned : catalogue.patterns()) {
			Catalogue.Pattern p = owned.value();
			String chapter = owned.chapter().id();
			String id = MOD + ":" + p.id();
			files.json(data.resolve("banner_pattern/" + p.id() + ".json"),
					obj("asset_id", id, "translation_key", "block." + MOD + ".banner." + p.id()));
			for (String[] dye : DYES) lang.put("block." + MOD + ".banner." + p.id() + "." + dye[0], dye[1] + " " + p.name());

			BufferedImage banner = art(chapter, "banner", p.id(), true);
			files.png(assets.resolve("textures/entity/banner/" + p.id() + ".png"), Masks.png(Masks.banner(banner)));
			BufferedImage shield = art(chapter, "shield", p.id(), false);
			files.png(assets.resolve("textures/entity/shield/" + p.id() + ".png"),
					Masks.png(shield != null ? Masks.shield(shield) : Masks.shieldFromBanner(banner)));

			if (p.item()) {
				files.json(data.resolve("tags/banner_pattern/pattern_item/" + p.id() + ".json"), tag(List.of(id)));
				BufferedImage icon = art(chapter, "item", p.id(), true);
				if (icon.getWidth() != 16 || icon.getHeight() != 16) {
					throw new IllegalStateException("art/kultur/" + chapter + "/item/" + p.id() + ".png is " + icon.getWidth() + "×" + icon.getHeight() + ", wanted 16×16");
				}
				String item = p.itemPath();
				files.png(assets.resolve("textures/item/" + item + ".png"), Masks.png(icon));
				files.json(assets.resolve("models/item/" + item + ".json"),
						obj("parent", "minecraft:item/generated", "textures", obj("layer0", MOD + ":item/" + item)));
				files.json(assets.resolve("items/" + item + ".json"),
						obj("model", obj("type", "minecraft:model", "model", MOD + ":item/" + item)));
				lang.put("item." + MOD + "." + item, p.name() + " Banner Pattern");
			} else {
				free.add(id);
			}
		}

		for (Catalogue.Owned<Catalogue.Painting> owned : catalogue.paintings()) {
			Catalogue.Painting q = owned.value();
			String chapter = owned.chapter().id();
			String id = MOD + ":" + q.id();
			files.json(data.resolve("painting_variant/" + q.id() + ".json"), obj(
					"asset_id", id, "width", q.width(), "height", q.height(),
					"title", obj("translate", "painting." + MOD + "." + q.id() + ".title"),
					"author", obj("translate", "painting." + MOD + "." + q.id() + ".author")));
			lang.put("painting." + MOD + "." + q.id() + ".title", q.title());
			lang.put("painting." + MOD + "." + q.id() + ".author", q.author());
			BufferedImage png = art(chapter, "painting", q.id(), true);
			if (png.getWidth() != 16 * q.width() || png.getHeight() != 16 * q.height()) {
				throw new IllegalStateException("art/kultur/" + chapter + "/painting/" + q.id() + ".png is " + png.getWidth() + "×" + png.getHeight()
						+ ", wanted " + (16 * q.width()) + "×" + (16 * q.height()) + " for " + q.width() + "×" + q.height() + " blocks");
			}
			files.png(assets.resolve("textures/painting/" + q.id() + ".png"), Masks.png(png));
			placeable.add(id);
		}

		files.json(minecraftData.resolve("tags/banner_pattern/no_item_required.json"), tag(free));
		files.json(minecraftData.resolve("tags/painting_variant/placeable.json"), tag(placeable));
		JsonObject langJson = new JsonObject();
		lang.forEach(langJson::addProperty);
		files.json(assets.resolve("lang/en_us.json"), langJson);
		return files.allOf();
	}

	private static JsonObject tag(List<String> values) {
		return obj("replace", false, "values", strings(values));
	}

	/** A PNG from {@code art/kultur/<chapter>/<kind>/<id>.png}; null when absent and not required. */
	private static BufferedImage art(String chapter, String kind, String id, boolean required) {
		String path = "/art/kultur/" + chapter + "/" + kind + "/" + id + ".png";
		try (InputStream in = GeneratedAssets.class.getResourceAsStream(path)) {
			if (in == null) {
				if (required) throw new IllegalStateException("missing art: src/main/resources" + path);
				return null;
			}
			return Masks.read(in);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
```

`KulturDataGenerator.java`:

```java
package metacraft.kultur.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/** {@code ./gradlew runDatagen}: writes every catalogue-derived file into src/main/generated. */
public final class KulturDataGenerator implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator generator) {
		generator.createPack().addProvider(GeneratedAssets::new);
	}
}
```

- [ ] **Step 3: Run datagen**

Run: `./gradlew :mods:metacraft-kultur:runDatagen`
Expected: BUILD SUCCESSFUL. Then check the output:

```bash
cd mods/metacraft-kultur/src/main/generated && find . -type f | sort
```

Expected file list, exactly:

```
./assets/kultur/items/pirkko_banner_pattern.json
./assets/kultur/lang/en_us.json
./assets/kultur/models/item/pirkko_banner_pattern.json
./assets/kultur/textures/entity/banner/itk.png
./assets/kultur/textures/entity/banner/pirkko.png
./assets/kultur/textures/entity/banner/qmisk.png
./assets/kultur/textures/entity/banner/tmeit.png
./assets/kultur/textures/entity/shield/itk.png
./assets/kultur/textures/entity/shield/pirkko.png
./assets/kultur/textures/entity/shield/qmisk.png
./assets/kultur/textures/entity/shield/tmeit.png
./assets/kultur/textures/item/pirkko_banner_pattern.png
./assets/kultur/textures/painting/draken.png
./data/kultur/banner_pattern/itk.json
./data/kultur/banner_pattern/pirkko.json
./data/kultur/banner_pattern/qmisk.json
./data/kultur/banner_pattern/tmeit.json
./data/kultur/painting_variant/draken.json
./data/kultur/tags/banner_pattern/pattern_item/pirkko.json
./data/minecraft/tags/banner_pattern/no_item_required.json
./data/minecraft/tags/painting_variant/placeable.json
```

(plus Fabric's `.cache/` folder). Then:

```bash
cat data/minecraft/tags/banner_pattern/no_item_required.json data/kultur/painting_variant/draken.json
python -c "import json;d=json.load(open('assets/kultur/lang/en_us.json'));print(len(d));print(d['block.kultur.banner.itk.light_blue'], '|', d['item.kultur.pirkko_banner_pattern'], '|', d['painting.kultur.draken.title'])"
```

Expected: the tag lists `kultur:itk`, `kultur:qmisk`, `kultur:tmeit` and not pirkko; the painting JSON has `asset_id`, `width` 3, `height` 4, translatable title and author; the lang has 67 keys (4×16 + 1 + 2), `Light Blue ITK`, `Pirkko Banner Pattern`, `The Guardian of Kistan`.

- [ ] **Step 4: Look at the textures**

Render the four generated banner and shield textures side by side at 4× and look at them (the Read tool shows PNGs):

```bash
python - <<'EOF'
from PIL import Image
G='mods/metacraft-kultur/src/main/generated/assets/kultur/textures/entity'
names=['itk','qmisk','tmeit','pirkko']
sheet=Image.new('RGBA',(128*4*2,128*2),(40,40,40,255))
for i,n in enumerate(names):
    for j,k in enumerate(['banner','shield']):
        im=Image.open(f'{G}/{k}/{n}.png').convert('RGBA'); sheet.alpha_composite(im,(i*256+j*128,0))
sheet.resize((sheet.width*2,sheet.height*2),Image.NEAREST).save('C:/Users/edvin/AppData/Local/Temp/claude/kultur_masks.png')
EOF
```

Expected: every banner shows the logo twice (front and back faces) and nothing else; every shield shows the logo once at the top-left; no stray dots. If a banner's back copy is missing or a logo is cut off, fix `Masks` before going on.

- [ ] **Step 5: The generated-assets guard**

Append to `mods/metacraft-kultur/build.gradle`:

```groovy
processResources {
	doFirst {
		def gen = file('src/main/generated/data/kultur/banner_pattern')
		def generating = gradle.startParameter.taskNames.any { it.endsWith('runDatagen') }
		if (!generating && (!gen.isDirectory() || gen.list().length == 0)) {
			throw new GradleException("Kultur: generated assets missing — run './gradlew runDatagen' first.")
		}
	}
}
```

- [ ] **Step 6: Whole-module check**

Run: `./gradlew :mods:metacraft-kultur:build`
Expected: BUILD SUCCESSFUL, all unit tests pass, jar produced under `mods/metacraft-kultur/build/libs/`.

- [ ] **Step 7: Checkpoint**

Tell Edvin Task 3 is done, that `src/main/generated` is to be committed with the sources, and point at the rendered sheet. Do not commit.

---

### Task 4: Runtime registration and the server game tests

**Files:**
- Create: `mods/metacraft-kultur/src/main/java/metacraft/kultur/content/PatternItem.java`
- Create: `mods/metacraft-kultur/src/main/java/metacraft/kultur/content/ModContent.java`
- Modify: `mods/metacraft-kultur/src/main/java/metacraft/kultur/Kultur.java`
- Create: `mods/metacraft-kultur/src/main/java/metacraft/kultur/gametest/KulturGameTests.java`

**Interfaces:**
- Consumes: `Catalogue`, generated assets.
- Produces: `ModContent.register(Catalogue)`, `ModContent.patternItem(String patternId) -> PatternItem | null`, `ModContent.items() -> Map<String, PatternItem>`, `Kultur.catalogue()`.

- [ ] **Step 1: Write the failing game tests**

`mods/metacraft-kultur/src/main/java/metacraft/kultur/gametest/KulturGameTests.java`:

```java
package metacraft.kultur.gametest;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import metacraft.kultur.Kultur;
import metacraft.kultur.catalogue.Catalogue;
import metacraft.kultur.content.ModContent;
import metacraft.kultur.content.PatternItem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BannerPatternTags;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPattern;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * What a server built from the catalogue actually holds: every pattern and painting in its
 * registry and its tag, every gated pattern behind an item whose component points at it alone,
 * and every asset the pack needs where Polymer will pick it up. Run:
 * {@code JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true" ./gradlew :mods:metacraft-kultur:runServer}.
 */
public final class KulturGameTests {
	@GameTest
	public void everyPatternIsRegisteredAndTagged(GameTestHelper helper) {
		Registry<BannerPattern> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		List<String> wrong = new ArrayList<>();
		for (Catalogue.Owned<Catalogue.Pattern> owned : Kultur.catalogue().patterns()) {
			Catalogue.Pattern p = owned.value();
			Identifier id = Identifier.fromNamespaceAndPath(Kultur.MOD_ID, p.id());
			var holder = registry.get(id);
			if (holder.isEmpty()) { wrong.add(p.id() + ": not in the banner pattern registry"); continue; }
			if (!holder.get().value().assetId().equals(id)) wrong.add(p.id() + ": asset id " + holder.get().value().assetId());
			boolean free = holder.get().is(BannerPatternTags.NO_ITEM_REQUIRED);
			if (free == p.item()) wrong.add(p.id() + ": no_item_required=" + free + " but item=" + p.item());
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	@GameTest
	public void gatedPatternsHaveTheirItem(GameTestHelper helper) {
		Registry<BannerPattern> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		List<String> wrong = new ArrayList<>();
		int gated = 0;
		for (Catalogue.Owned<Catalogue.Pattern> owned : Kultur.catalogue().patterns()) {
			Catalogue.Pattern p = owned.value();
			PatternItem item = ModContent.patternItem(p.id());
			if (!p.item()) {
				if (item != null) wrong.add(p.id() + ": free pattern has an item");
				continue;
			}
			gated++;
			if (item == null) { wrong.add(p.id() + ": no item"); continue; }
			Identifier itemId = Identifier.fromNamespaceAndPath(Kultur.MOD_ID, p.itemPath());
			if (BuiltInRegistries.ITEM.getValue(itemId) != item) wrong.add(p.id() + ": item not registered as " + itemId);
			ItemStack stack = new ItemStack(item);
			HolderSet<BannerPattern> provides = stack.get(DataComponents.PROVIDES_BANNER_PATTERNS);
			if (provides == null) { wrong.add(p.id() + ": item has no provides_banner_patterns"); continue; }
			List<Identifier> ids = provides.stream().map(h -> h.unwrapKey().map(ResourceKey::identifier).orElse(null)).toList();
			Identifier want = Identifier.fromNamespaceAndPath(Kultur.MOD_ID, p.id());
			if (ids.size() != 1 || !want.equals(ids.getFirst())) wrong.add(p.id() + ": item provides " + ids + ", wanted [" + want + "]");
			TagKey<BannerPattern> tag = TagKey.create(Registries.BANNER_PATTERN, Identifier.fromNamespaceAndPath(Kultur.MOD_ID, "pattern_item/" + p.id()));
			if (registry.get(tag).isEmpty()) wrong.add(p.id() + ": tag " + tag + " missing");
		}
		if (gated == 0) wrong.add("no gated pattern in the catalogue to test");
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	@GameTest
	public void everyPaintingIsRegisteredAndPlaceable(GameTestHelper helper) {
		Registry<PaintingVariant> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT);
		List<String> wrong = new ArrayList<>();
		for (Catalogue.Owned<Catalogue.Painting> owned : Kultur.catalogue().paintings()) {
			Catalogue.Painting q = owned.value();
			Identifier id = Identifier.fromNamespaceAndPath(Kultur.MOD_ID, q.id());
			var holder = registry.get(id);
			if (holder.isEmpty()) { wrong.add(q.id() + ": not in the painting variant registry"); continue; }
			PaintingVariant v = holder.get().value();
			if (v.width() != q.width() || v.height() != q.height()) wrong.add(q.id() + ": " + v.width() + "×" + v.height() + " in the registry, " + q.width() + "×" + q.height() + " in the catalogue");
			if (v.title().isEmpty() || v.author().isEmpty()) wrong.add(q.id() + ": title or author missing");
			if (!holder.get().is(PaintingVariantTags.PLACEABLE)) wrong.add(q.id() + ": not placeable");
		}
		if (!wrong.isEmpty()) helper.fail(String.join("; ", wrong));
		helper.succeed();
	}

	/** Polymer copies the mod's assets wholesale, so an asset on the classpath is an asset in the pack. */
	@GameTest
	public void everyAssetIsInTheJar(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		for (Catalogue.Owned<Catalogue.Pattern> owned : Kultur.catalogue().patterns()) {
			Catalogue.Pattern p = owned.value();
			need(missing, "textures/entity/banner/" + p.id() + ".png");
			need(missing, "textures/entity/shield/" + p.id() + ".png");
			if (p.item()) {
				need(missing, "textures/item/" + p.itemPath() + ".png");
				need(missing, "models/item/" + p.itemPath() + ".json");
				need(missing, "items/" + p.itemPath() + ".json");
			}
		}
		for (Catalogue.Owned<Catalogue.Painting> owned : Kultur.catalogue().paintings()) {
			need(missing, "textures/painting/" + owned.value().id() + ".png");
		}
		need(missing, "lang/en_us.json");
		if (!missing.isEmpty()) helper.fail("missing from assets/kultur: " + missing);
		if (!PolymerResourcePackUtils.hasResources()) helper.fail("Polymer has no mod assets registered");
		if (!PolymerResourcePackUtils.isRequired()) helper.fail("the pack is not marked required");
		helper.succeed();
	}

	private static void need(List<String> missing, String path) {
		try (InputStream in = Kultur.class.getResourceAsStream("/assets/" + Kultur.MOD_ID + "/" + path)) {
			if (in == null) missing.add(path);
		} catch (java.io.IOException e) {
			missing.add(path + " (" + e + ")");
		}
	}
}
```

- [ ] **Step 2: Run the game tests to see them fail**

Run: `JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true" ./gradlew :mods:metacraft-kultur:runServer`
Expected: compilation failure (`ModContent`, `PatternItem`, `Kultur.catalogue()` missing).

- [ ] **Step 3: The item and its registration**

`PatternItem.java`:

```java
package metacraft.kultur.content;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A banner pattern item the loom asks for before it offers the pattern — vanilla's globe and
 * flower mechanic, for a chapter pattern that is meant to be found rather than free. A vanilla
 * client is shown the flower pattern item wearing our model; the component that matters,
 * {@code provides_banner_patterns}, is on the item's default components and needs no disguise,
 * since the loom reads it on the server.
 */
public final class PatternItem extends Item implements PolymerItem {
	public final String patternId;
	private final Identifier model;

	public PatternItem(Properties properties, String patternId, Identifier model) {
		super(properties);
		this.patternId = patternId;
		this.model = model;
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		return Items.FLOWER_BANNER_PATTERN;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		return model;
	}
}
```

`ModContent.java`:

```java
package metacraft.kultur.content;

import metacraft.kultur.Kultur;
import metacraft.kultur.catalogue.Catalogue;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.entity.BannerPattern;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The pattern items, one per catalogue pattern marked {@code item}; everything else is data. */
public final class ModContent {
	private static final Map<String, PatternItem> ITEMS = new LinkedHashMap<>();

	private ModContent() {}

	public static void register(Catalogue catalogue) {
		for (Catalogue.Owned<Catalogue.Pattern> owned : catalogue.patterns()) {
			Catalogue.Pattern p = owned.value();
			if (!p.item()) continue;
			Identifier itemId = Identifier.fromNamespaceAndPath(Kultur.MOD_ID, p.itemPath());
			TagKey<BannerPattern> tag = TagKey.create(Registries.BANNER_PATTERN, Identifier.fromNamespaceAndPath(Kultur.MOD_ID, "pattern_item/" + p.id()));
			// The tag resolves against the dynamic registry, which does not exist when items are
			// registered — hence the delayed component, the way vanilla attaches its own pattern tags.
			Item.Properties props = new Item.Properties()
					.setId(ResourceKey.create(Registries.ITEM, itemId))
					.stacksTo(1)
					.rarity(Rarity.RARE)
					.delayedComponent(DataComponents.PROVIDES_BANNER_PATTERNS, lookup -> lookup.getOrThrow(tag));
			PatternItem item = Registry.register(BuiltInRegistries.ITEM, itemId, new PatternItem(props, p.id(), itemId));
			ITEMS.put(p.id(), item);
		}
	}

	/** The item gating a pattern, by pattern id; null for a free pattern. */
	public static @Nullable PatternItem patternItem(String patternId) {
		return ITEMS.get(patternId);
	}

	public static Map<String, PatternItem> items() {
		return Collections.unmodifiableMap(ITEMS);
	}
}
```

`Kultur.java` becomes:

```java
package metacraft.kultur;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import metacraft.kultur.catalogue.Catalogue;
import metacraft.kultur.content.ModContent;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kultur: chapter culture for vanilla clients via Polymer — banner and shield patterns and
 * paintings now, drinks later. Everything the client sees is in the data files and the pack, so
 * the mod's own job is small: read the catalogue, register the pattern items, hand Polymer the assets.
 */
public class Kultur implements ModInitializer {
	public static final String MOD_ID = "kultur";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static Catalogue catalogue;

	@Override
	public void onInitialize() {
		catalogue = Catalogue.load();
		ModContent.register(catalogue);
		PolymerResourcePackUtils.addModAssets(MOD_ID);
		PolymerResourcePackUtils.markAsRequired();
		LOGGER.info("[{}] {} chapter(s), {} pattern(s) ({} with items), {} painting(s)", MOD_ID,
				catalogue.chapters().size(), catalogue.patterns().size(), ModContent.items().size(), catalogue.paintings().size());
	}

	/** The catalogue the running server was built from. */
	public static Catalogue catalogue() {
		if (catalogue == null) catalogue = Catalogue.load();
		return catalogue;
	}
}
```

If `delayedComponent` does not accept the lambda as written, the target type is `DataComponentInitializers.SingleComponentInitializer<HolderSet<BannerPattern>>` whose single method is `create(HolderLookup.Provider)`; `HolderLookup.Provider.getOrThrow(TagKey)` returns `HolderSet.Named`, which is what vanilla's `Items` does for `flower_banner_pattern`.

- [ ] **Step 4: Run the game tests to see them pass**

Run: `JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true" ./gradlew :mods:metacraft-kultur:runServer`
Expected: the server log shows `[kultur] 3 chapter(s), 4 pattern(s) (1 with items), 1 painting(s)`, then the four tests under `metacraft.kultur.gametest` pass and the server exits 0. If `./run` is locked by another dev server, pass `-PrunDir=run-tests` after adding to `build.gradle` the same `loom { runs { server { runDir = project.findProperty('runDir') ?: 'run' } } }` block ovvar has.

- [ ] **Step 5: Checkpoint**

Tell Edvin Task 4 is done and how the tests were run. Do not commit.

---

### Task 5: Client screenshot test

The rule for this repo is to look at what a vanilla client draws, not to argue from source. This is ovvar's client-test recipe, reduced to one scene: a banner with the ITK pattern, a shield with the Pirkko pattern in the player's hand, and the Draken painting, screenshotted from a fixed spot.

**Files:**
- Modify: `mods/metacraft-kultur/build.gradle` (client test source set)
- Create: `mods/metacraft-kultur/src/gametest/resources/fabric.mod.json`
- Create: `mods/metacraft-kultur/src/gametest/resources/kultur-clienttest.mixins.json`
- Create: `mods/metacraft-kultur/src/gametest/java/metacraft/kultur/clienttest/mixin/PolymerHelloMixin.java`
- Create: `mods/metacraft-kultur/src/gametest/java/metacraft/kultur/clienttest/mixin/ServerPackAutoAcceptMixin.java`
- Create: `mods/metacraft-kultur/src/gametest/java/metacraft/kultur/clienttest/KulturClientTests.java`

- [ ] **Step 1: The source set**

Add to `mods/metacraft-kultur/build.gradle`, after `setupRunDatagen("kultur")`:

```groovy
// Client screenshot test: a real client joins an in-process dedicated server and a screenshot of a
// banner, a shield and a painting is taken. Source set src/gametest, task runClientGameTest (needs a display).
fabricApi {
	configureTests {
		createSourceSet = true
		modId = "kultur-clienttest"
		enableGameTests = false
		enableClientGameTests = true
		eula = true
		username = "Tester"
	}
}
```

- [ ] **Step 2: The test mod's metadata and mixins**

`src/gametest/resources/fabric.mod.json`:

```json
{
	"schemaVersion": 1,
	"id": "kultur-clienttest",
	"version": "1.0.0",
	"name": "Kultur client tests",
	"description": "Screenshot test: a client sees what the server draws.",
	"license": "MIT",
	"environment": "client",
	"entrypoints": {
		"fabric-client-gametest": [
			"metacraft.kultur.clienttest.KulturClientTests"
		]
	},
	"mixins": [
		"kultur-clienttest.mixins.json"
	],
	"depends": {
		"kultur": "*",
		"fabric-client-gametest-api-v1": "*"
	}
}
```

`src/gametest/resources/kultur-clienttest.mixins.json`:

```json
{
	"required": true,
	"minVersion": "0.8",
	"package": "metacraft.kultur.clienttest.mixin",
	"compatibilityLevel": "JAVA_25",
	"client": [
		"PolymerHelloMixin",
		"ServerPackAutoAcceptMixin"
	],
	"injectors": {
		"defaultRequire": 1
	}
}
```

`mixin/PolymerHelloMixin.java`:

```java
package metacraft.kultur.clienttest.mixin;

import eu.pb4.polymer.networking.impl.client.ClientPacketRegistry;
import eu.pb4.polymer.networking.impl.packets.HelloS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The test client has Polymer on its classpath, and a server treats a client as Polymer-aware the
 * moment it answers the server's hello. Swallowing the hello keeps the server on the vanilla path
 * for this client, which is what the screenshot must judge. Test source set only; never ships.
 */
@Mixin(ClientPacketRegistry.class)
public abstract class PolymerHelloMixin {
	@Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
	private static void kultur$stayVanilla(Minecraft client, ClientCommonPacketListenerImpl listener, HelloS2CPayload payload, CallbackInfo ci) {
		ci.cancel();
	}
}
```

`mixin/ServerPackAutoAcceptMixin.java`:

```java
package metacraft.kultur.clienttest.mixin;

import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The test client accepts every server's resource pack without the prompt: the connect step of
 * the test API blocks until the world loads, and the pack prompt would block it first. Test source set only.
 */
@Mixin(ServerData.class)
public abstract class ServerPackAutoAcceptMixin {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void kultur$acceptPacks(CallbackInfo ci) {
		((ServerData) (Object) this).setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
	}
}
```

- [ ] **Step 3: The test**

`KulturClientTests.java`:

```java
package metacraft.kultur.clienttest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.ConfirmScreen;

import java.nio.file.Path;
import java.util.Properties;

/**
 * What a vanilla client sees of the chapter patterns. A dedicated server runs in-process with
 * kultur; the client joins it as a vanilla client (PolymerHelloMixin), accepts the pack, and looks
 * at a white banner with the ITK pattern, holds a shield with the Pirkko pattern, and has the Draken
 * painting on the wall beside them. The screenshot is judged by eye — open it and look.
 *
 * Run: {@code ./gradlew :mods:metacraft-kultur:runClientGameTest} (opens a window). Screenshots
 * land in {@code mods/metacraft-kultur/build/run/clientGameTest/screenshots}.
 */
public final class KulturClientTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		props.setProperty("server-port", "25597");
		props.setProperty("level-type", "minecraft:flat");
		props.setProperty("spawn-monsters", "false");
		props.setProperty("spawn-animals", "false");
		props.setProperty("difficulty", "peaceful");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection conn = server.connect()) {
				acceptResourcePack(ctx);
				conn.waitForChunksRender();
				server.runCommand("gamerule advance_time false");
				server.runCommand("time set noon");
				server.runCommand("gamemode creative Tester");
				// A wall behind the scene so the painting has something to hang on, a banner in
				// front of it with ITK in black on white, and the painting on the wall to the east.
				server.runCommand("fill -3 -60 4 3 -56 4 minecraft:stone");
				server.runCommand("setblock 0 -60 3 minecraft:white_banner[rotation=8]{patterns:[{pattern:\"kultur:itk\",color:\"black\"}]}");
				server.runCommand("summon minecraft:painting 2.5 -58.5 3.9 {facing:2b,variant:\"kultur:draken\"}");
				// The Pirkko pattern on a shield in the main hand, held so it shows in first person.
				server.runCommand("item replace entity Tester weapon.mainhand with minecraft:shield[minecraft:base_color=\"white\",minecraft:banner_patterns=[{pattern:\"kultur:itk\",color:\"black\"},{pattern:\"kultur:pirkko\",color:\"red\"}]]");
				server.runCommand("tp Tester 0.5 -60 -1.5 0 10");   // look south (+Z) at the banner, slightly down
				ctx.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
				conn.waitForClientboundPackets();
				ctx.waitTicks(60);
				Path shot = ctx.takeScreenshot(TestScreenshotOptions.of("kultur_banner_shield_painting").withSize(1920, 1080));
				System.out.println("[kultur-clienttest] screenshot: " + shot);
			}
		}
	}

	/** The pack is accepted unasked (ServerPackAutoAcceptMixin); wait for it to download and apply. */
	private static void acceptResourcePack(ClientGameTestContext ctx) {
		ctx.waitTicks(20);
		ctx.waitFor(client -> client.gui.overlay() == null && !(client.gui.screen() instanceof ConfirmScreen), 20 * 90);
		ctx.waitTicks(20);
	}
}
```

- [ ] **Step 4: Run it and look**

Run: `./gradlew :mods:metacraft-kultur:runClientGameTest`
Expected: BUILD SUCCESSFUL and a PNG at `mods/metacraft-kultur/build/run/clientGameTest/screenshots/kultur_banner_shield_painting.png`. Open it with the Read tool. What must be there: the ITK text in black on a white banner (front face, readable), the painting of the dragon on the stone wall, and a shield in the hand with ITK and the Pirkko ghost. If a pattern renders as a solid colour or a missing-texture magenta square, the texture path or the normaliser's region is wrong: check the pack Polymer wrote under `mods/metacraft-kultur/build/run/clientGameTest/polymer/resource_pack.zip` (or `run/…`) for `assets/kultur/textures/entity/banner/itk.png` before touching code. If the pirkko shield item component syntax is refused by 26.3, use `/give Tester kultur:pirkko_banner_pattern` and a loom instead: the screenshot is the deliverable, not the command.

Note the shield's pattern draws from the shield pattern texture and the banner's from the banner one, so both normalisers are exercised by this one frame; ITK is drawn on both to compare the hand-drawn shield with the banner.

- [ ] **Step 5: Checkpoint**

Tell Edvin Task 5 is done and give him the screenshot path. Do not commit.

---

### Task 6: README

**Files:**
- Create: `mods/metacraft-kultur/README.md`

- [ ] **Step 1: Write it**

Written for a Data or Media student who has never opened this repo. Keep it to the facts below, in this order, in plain English with the repo's README voice (see `mods/moredyes/README.md` for tone). Use the exact paths and sizes; no placeholders.

```markdown
# Kultur

METAmods module `mods/metacraft-kultur` (mod id `kultur`). Chapter culture for vanilla clients:
banner and shield patterns for the chapters and their clubs, and paintings. Fabric +
[Polymer](https://polymer.pb4.eu), Minecraft 26.3, Java 25. Players need only the auto-served
resource pack; no client mod. The successor of PolymITer; the ovvar and patches from there live in
`mods/ovvar`.

## What is here, and what is not

IT's content: ITK, QMISK and TMEIT banner patterns free in any loom, the Pirkko pattern behind a
pattern item (`kultur:pirkko_banner_pattern`, from creative or `/give` for now), and Emelie Stark's
painting "The Guardian of Kistan" (3×4). Data and Media have their places in the catalogue and
nothing in them yet — that is what this file is for.

Not here yet, on purpose: loot tables for the pattern items, the chapter drinks (they will be a
Patbox's Brewery datapack in this module), any Data or Media art.

With `moredyes` on the same server every pattern here also comes in Cerise and Laserviolet through
its dye loom; nothing in this module knows about that.

## Adding a banner pattern

1. Draw the pattern on a copy of a vanilla banner texture: 64×64 (or 128×128; any multiple of 64
   works, and the regions below scale with it). The flag's front face is the 20×40 area at (1,1).
   Draw there and nowhere else; datagen copies the front onto the back face for you and clears
   the rest.
2. Save it as `src/main/resources/art/kultur/<chapter>/banner/<id>.png`. `<id>` is lowercase
   letters, digits and underscores, unique across every chapter (`slaggan`, not `släggan`).
3. Optionally draw a shield version, `.../shield/<id>.png`, same size, in the shield's 12×22 face
   at (1,1) (vanilla stays inside the 10×20 at (2,2)). Without one, the banner's front face is
   scaled to half size onto the shield, which is fine for bold logos and poor for thin text.
4. Add a line to `src/main/resources/kultur.json` under your chapter:
   `{ "id": "<id>", "name": "<Name>" }`. Add `"item": true` to make it need a pattern item in the
   loom, and then also draw a 16×16 icon at `.../item/<id>.png`.
5. `./gradlew runDatagen` from the repo root, then commit `src/main/generated` with your art.

Names: the lang file is generated from `name` — "Light Blue ITK" and so on for all sixteen dyes,
and "<Name> Banner Pattern" for the item.

## Adding a painting

1. A PNG at 16 pixels per block: 48×64 for a 3×4 painting. Save it as
   `src/main/resources/art/kultur/<chapter>/painting/<id>.png`.
2. `{ "id": "<id>", "title": "...", "author": "...", "width": 3, "height": 4 }` under your chapter in
   `kultur.json`.
3. `./gradlew runDatagen`.

Every painting is placeable; a player gets it the vanilla way (a painting item with the variant, or
luck with a blank painting on a wall big enough).

## Adding a chapter

`{ "id": "<chapter>", "name": "<Chapter>", "patterns": [], "paintings": [] }` in `kultur.json`, and a
folder `art/kultur/<chapter>/`.

## Building and testing

- `./gradlew :mods:metacraft-kultur:build` — needs `runDatagen` to have run; the build says so if not.
- `./gradlew :mods:metacraft-kultur:test` — unit tests: the catalogue's validation and the texture
  normaliser (`Masks`).
- `JAVA_TOOL_OPTIONS="-Dfabric-api.gametest=true" ./gradlew :mods:metacraft-kultur:runServer` —
  server game tests (`KulturGameTests`): every catalogue entry in its registry and tag, gated
  patterns behind their item, every asset in the jar.
- `./gradlew :mods:metacraft-kultur:runClientGameTest` — a real client joins a test server and
  screenshots a banner, a shield and the painting into
  `build/run/clientGameTest/screenshots/`. Look at it; that is the test.
- `./gradlew :mods:metacraft-kultur:runServer` — a dev server with the pack auto-hosted; join with a
  vanilla client, `/give @s minecraft:loom`, `/give @s kultur:pirkko_banner_pattern`.

## How it works

`kultur.json` is the only list. `GeneratedAssets` (datagen) writes, per pattern, the
`banner_pattern` definition, the tag entries, the normalised banner and shield textures and sixteen
lang lines; per painting, the `painting_variant` definition, its texture and two lang lines; per
gated pattern, the item's model and icon. Banner patterns and painting variants are dynamic
registries the server syncs to every client, so a vanilla client needs nothing but the textures,
which Polymer serves in the pack. The only Java that runs on a server is `ModContent`: one Polymer
item per gated pattern, disguised as the vanilla flower pattern item with our model, carrying
`provides_banner_patterns` for its tag.
```

- [ ] **Step 2: Check every command in it**

Run each of the five commands under "Building and testing" once more from the repo root; each must do what the README says. Fix the README, not the claim, if one does not.

- [ ] **Step 3: Checkpoint**

Tell Edvin Task 6 is done, that the whole slice is complete, and list everything to stage: `settings.gradle`, `gradle.properties`, `mods/metacraft-kultur/` (sources, art, `src/main/generated`, tests, README) and the spec and plan under `docs/superpowers/`. Do not commit.
