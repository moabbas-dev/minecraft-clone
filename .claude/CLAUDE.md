# Minecraft Project — Research, Accuracy & Subagent Orchestration Rules

## Core Principle

When working on this Minecraft project, **do not invent, assume, approximate, or creatively fabricate Minecraft behavior, functionality, mechanics, formulas, visuals, designs, particles, textures, colors, animations, or other game details.**

The default workflow must always be:

**Research → Verify → Inspect references → Calculate/derive if necessary → Plan → Implement → Verify**

Never use:

**Assume → Invent → Implement**

---

# 1. Minecraft Source of Truth

Treat **minecraft.wiki and minecraftdocs.dev** as the primary source of truth for Minecraft-related information.

Before implementing or modifying anything related to Minecraft:

1. **Research first.**
2. Use the configured **Perplexity MCP/search tools** as the primary search method.
3. Search `minecraft.wiki and minecraftdocs.dev` for the relevant mechanic, block, entity, item, structure, particle, visual, formula, behavior, or other information.
4. Read the relevant documentation before writing code.
5. Base the implementation on documented Minecraft behavior rather than assumptions, memory, or general knowledge.
6. If the information is unclear, perform additional searches and inspect related wiki pages before implementing.

Prefer narrow searches targeting the exact feature.

Example searches:

- `site:minecraft.wiki Enderman teleportation mechanics Java Edition`
- `site:minecraft.wiki Nether portal coordinate conversion`
- `site:minecraft.wiki redstone signal strength distance`
- `site:minecraft.wiki explosion damage calculation`
- `site:minecraft.wiki critical hit particles`
- `site:minecraft.wiki enchanting mechanics formula`

When necessary, perform multiple searches rather than relying on a single result.

---

# 2. Functionality, Mechanics & Calculations

For anything involving game behavior or functionality, search the documentation first.

This includes, but is not limited to:

- game mechanics
- block behavior
- entity behavior
- spawning
- movement
- physics
- dimensions
- biomes
- structures
- world generation
- coordinates
- distances
- light levels
- damage
- health
- hunger
- experience
- enchantments
- redstone
- ticks and timing
- probabilities
- drop rates
- ranges
- formulas
- mathematical calculations
- game limits
- version-specific behavior

Never create a formula, constant, probability, range, timing value, or game rule from intuition when it can be researched.

If a calculation is required:

1. Research the underlying Minecraft rules.
2. Find the documented values/constants/formulas.
3. Verify the edition and version.
4. Derive the calculation from those facts.
5. Only then implement it.

---

# 3. Visual Accuracy

Do not invent Minecraft visuals.

This applies to:

- blocks
- items
- entities
- mobs
- structures
- particles
- effects
- textures
- UI elements
- icons
- colors
- shapes
- proportions
- layouts
- environmental details
- animations

When implementing something visual:

1. Search for the relevant Minecraft Wiki documentation.
2. Find available reference images.
3. Pull/open the images when the available tools allow it.
4. Actually inspect/read the images rather than relying only on filenames, alt text, search snippets, or descriptions.
5. Use those images as visual references for the implementation.
6. If multiple reference images are available, inspect enough of them to understand the actual Minecraft design.

Do not create something merely because it "looks Minecraft-like."

---

# 4. No Hallucinated Minecraft Content

Never silently fill missing information with something that seems reasonable.

If information cannot be verified:

1. Search again using different terminology.
2. Inspect related Minecraft Wiki pages.
3. Follow references to related mechanics.
4. Check whether the behavior differs between Java Edition and Bedrock Edition.
5. Check whether it changed between Minecraft versions.
6. Use additional authoritative documentation only when necessary.

If reliable information still cannot be found, **tell the user exactly what could not be verified instead of inventing it.**

---

# 5. Edition & Version Awareness

Minecraft behavior may differ between:

- Java Edition
- Bedrock Edition
- different Minecraft versions

Always determine whether researched information applies to the edition/version used by this project.

Do not mix Java and Bedrock behavior unless explicitly required.

If the project's target edition/version is already known, preserve it consistently.

If a feature changed between versions, research the version relevant to the project rather than automatically using the newest behavior.

---

# 6. Existing Project Code

Researching Minecraft behavior does **not** replace inspecting the existing project.

Before modifying functionality:

1. Inspect the relevant existing code.
2. Understand the current architecture and data flow.
3. Research the corresponding Minecraft behavior.
4. Compare the current implementation with the documented behavior.
5. Determine the smallest correct change.
6. Implement it consistently with the existing project architecture.

Do not rewrite working systems unnecessarily.

Do not create duplicate functionality when the project already contains an appropriate implementation or abstraction.

---

# 7. Subagent Orchestration

For large or multi-part tasks, **do not perform all work sequentially in the main agent when independent work can be delegated.**

Before starting a large task:

1. Analyze the complete request.
2. Break it into logical subtasks.
3. Identify which subtasks depend on each other.
4. Identify which subtasks can be researched or executed independently.
5. Delegate independent work to appropriate subagents.
6. Run independent subtasks in parallel when possible.
7. Keep the main agent responsible for coordination, integration, conflict resolution, and final verification.

Do not create subagents merely for the sake of creating subagents. Use them when they improve parallelism, context isolation, research depth, or implementation quality.

---

# 8. When to Use Subagents

Use subagents aggressively for substantial independent workstreams such as:

- researching Minecraft mechanics
- researching formulas, constants, probabilities, or calculations
- researching Minecraft Wiki documentation
- finding visual references
- pulling and inspecting Minecraft reference images
- investigating existing project code
- analyzing separate systems or features
- implementing independent components
- writing or updating tests
- checking version differences
- verifying an implementation against documented Minecraft behavior

Do not make one agent research several unrelated topics sequentially when those topics can be researched independently.

---

# 9. Minecraft Research Delegation

For large Minecraft tasks, prefer parallel research.

Depending on the task, useful subagent roles may include:

### Mechanics Research Agent

Research the exact Minecraft behavior on `minecraft.wiki or minecraftdocs.dev` using Perplexity.

Determine:

- rules
- conditions
- edge cases
- edition differences
- version differences

### Calculation Research Agent

Research documented:

- formulas
- constants
- probabilities
- ranges
- coordinate rules
- timings
- tick behavior
- numerical limits

Do not derive calculations until the underlying documented values have been verified.

### Visual Research Agent

Research the relevant visual references.

The agent should:

1. Find Minecraft Wiki pages and images.
2. Pull/open available reference images.
3. Actually inspect the images.
4. Report concrete visual characteristics needed for implementation.
5. Avoid inventing missing visual details.

### Codebase Analysis Agent

Inspect the existing project and determine:

- relevant files
- existing abstractions
- current behavior
- integration points
- dependencies
- reusable functionality
- the smallest appropriate implementation approach

### Implementation Agent

Implement a well-defined independent portion of the task after the necessary research is available.

Implementation agents must receive the verified requirements they need and must not invent missing Minecraft behavior.

### Verification Agent

After implementation, independently compare the result against:

- Minecraft Wiki documentation
- verified calculations
- inspected visual references
- project architecture
- the original user request

These are possible roles, not mandatory agents that must always be created. Create only the agents relevant to the task.

---

# 10. Parallelism

When multiple subtasks do not depend on each other's results, launch them in parallel rather than waiting for each one sequentially.

For example, if asked to implement a Nether portal visualization, a preferred decomposition might be:

Main Agent

- Subagent: research Nether portal mechanics
- Subagent: research coordinate conversion formulas
- Subagent: inspect Minecraft Wiki visual references
- Subagent: inspect the existing project implementation

After those independent investigations finish, the main agent should combine the findings and determine the implementation plan.

Do **not** parallelize tasks that strongly depend on unfinished results from another task merely to increase the number of agents.

---

# 11. Research Rules Apply to Every Subagent

Every subagent working on Minecraft-related material must follow the same research-first rules.

Subagents must not invent:

- mechanics
- functionality
- calculations
- constants
- designs
- textures
- particles
- animations
- colors
- dimensions
- behavior
- visual details

Minecraft-related research must prioritize `minecraft.wiki and minecraftdocs.dev` through the configured Perplexity tools.

Visual research agents must inspect available reference images when visual accuracy matters.

A subagent's confidence is **not** a substitute for documentation.

---

# 12. Main Agent Responsibility

The main agent remains responsible for the final result.

Never blindly combine subagent outputs.

After subagents finish:

1. Review their findings.
2. Check that claims are supported by the researched sources.
3. Resolve contradictions between agents.
4. Verify important Minecraft facts against the source material.
5. Ensure edition/version consistency.
6. Determine the final implementation.
7. Integrate changes consistently with the existing architecture.
8. Run appropriate tests/checks.
9. Verify the finished result against the original request.
10. Verify that no undocumented Minecraft behavior or visual details were invented.

Subagents are for **parallel investigation, context isolation, and independent work**, not for reducing verification quality.

---

# 13. Required Workflow for Large Minecraft Tasks

For a substantial Minecraft task, prefer this workflow:

### Phase 1 — Understand

- Inspect the request.
- Inspect relevant project code.
- Identify the Minecraft systems involved.
- Identify unknown facts that require research.

### Phase 2 — Decompose

Split the task into independent research and implementation workstreams.

### Phase 3 — Parallel Research

Delegate independent research where useful, especially:

- mechanics
- calculations
- visuals
- version-specific behavior
- codebase architecture

### Phase 4 — Verify

Review research results and resolve inconsistencies before implementation.

### Phase 5 — Plan

Create an implementation approach based on:

- verified Minecraft behavior
- verified calculations
- inspected visual references
- existing project architecture

### Phase 6 — Implement

Implement the smallest correct solution.

Independent implementation work may be delegated when safe.

### Phase 7 — Validate

Run tests/checks and compare the result against the researched Minecraft references.

### Phase 8 — Final Review

Before considering the task complete, confirm:

- No Minecraft mechanics were invented.
- No formulas/constants were guessed.
- No visual details were invented.
- Relevant images were inspected when visual accuracy mattered.
- Correct edition/version behavior was used.
- Existing project architecture was respected.
- Subagent findings were reviewed rather than blindly accepted.
- The implementation satisfies the original request.

---


---

# 14. Minecraft 26.3 Zero-Invention & Existing-Code Cleanup

Target **Minecraft Java Edition 26.3**. Reproduce verified vanilla behavior; never create merely Minecraft-like behavior.

## Research Priority
Use **Perplexity MCP** and research in this order: (1) actual Minecraft Java 26.3 source/implementation, (2) actual vanilla 26.3 worldgen/configuration data, (3) `minecraftdocs.dev` for architecture, and (4) `minecraft.wiki` for mechanics, version differences and visuals. Search repeatedly and cross-check; a Wiki summary is not enough when the internal algorithm is required. For worldgen, trace relevant vanilla systems such as `TreeFeature`, trunk/foliage/root placers, configured/placed features, `TreePlacements`, `VegetationPlacements`, `Climate`, `Climate.Sampler`, `MultiNoiseBiomeSource`, `OverworldBiomeBuilder`, `NoiseRouter`, `NoiseChunk`, density functions, surface rules, aquifers, noise settings, RNG and seed derivation.

## Delete Invented/Guessed Minecraft Logic
Existing project code is not automatically trusted. Audit affected systems for AI guesses, approximations, placeholders, arbitrary constants, simplified algorithms and trial-and-error tuning. When Minecraft-specific code is verified to be invented, guessed, approximate or incorrect, **remove it and replace it with researched vanilla 26.3 behavior**. Also remove obsolete helpers, constants, dead branches, stale comments and tests that only support the fake implementation. Do not keep the old approximation as a fallback, comment it out “just in case”, or layer correct logic over fake logic.

Do not delete legitimate project infrastructure blindly. Rendering, caching, networking, UI, abstractions and optimizations may differ from Mojang. Delete code because its Minecraft behavior is verified wrong/invented, not merely because its architecture differs.

## No Arbitrary Substitutions
Minecraft constants must have a verified source. Never tune values until the result looks right. Do not substitute generic Perlin/Simplex noise, Voronoi regions, random blobs, spherical foliage, custom climate thresholds or generic tree generators unless research confirms vanilla 26.3 uses that behavior. Complexity is not permission to simplify or invent.

## Trees
Never manually design trees from guessed heights, spherical/circular foliage, random leaf deletion or arbitrary branches. Verify each tree's configured/placed feature, trunk placer, foliage placer, parameters, feature size, roots, decorators, block providers, placement conditions, biome placement and RNG behavior. Audit and remove fake tree generation after the vanilla replacement is verified.

## Biomes & Terrain
Never use simplified rules such as `high temperature -> desert`, generic noise maps, fake climate systems or manually tuned terrain curves merely because they resemble Minecraft. Research the actual 26.3 climate, biome-selection, terrain, density/noise, surface, cave, aquifer and RNG pipelines. Audit arbitrary thresholds, guessed cave/river logic, custom noise settings and unexplained constants and replace verified approximations.

## Randomness
RNG is part of the algorithm. Research world/chunk/feature seed derivation, salts, positional randomness, random factories and random-consumption order where relevant. Never substitute `Math.random()` or another convenient RNG without verified justification.

## Mandatory Stop Condition
If exact 26.3 behavior cannot be verified after thorough research, **STOP that part of implementation**. Do not fill gaps with intuition, plausible algorithms, standard game-development behavior or visual approximations. Report what remains unverified. An incomplete verified implementation is preferable to a completed fake implementation.

## Worldgen Workflow
**Research vanilla 26.3 → inspect vanilla data → inspect existing code → identify unsupported approximations → plan safe replacement → implement verified behavior → delete obsolete fake code → test → compare against vanilla references.**

For large work, use parallel subagents for source research, worldgen data, trees, biome/climate, terrain/density, RNG/seeding, visual references, existing-code audit and verification. The main agent must reconcile their findings.

## Completion Gate
Do not mark work complete until no affected mechanics/algorithms/constants are guessed, fake tree/biome/terrain logic has been replaced, relevant 26.3 source/data was inspected, RNG was verified where relevant, obsolete fake code was cleaned up, and important Minecraft-specific decisions can be traced to researched vanilla behavior.

**Golden rule: Do not build Minecraft-like behavior. Reproduce researched Minecraft Java Edition 26.3 behavior. If you cannot verify it, do not invent it.**


# Final Rule

**Minecraft Wiki documentation and inspected Minecraft references take precedence over assumptions, memory, creativity, convenience, or model confidence.**

When uncertain: **search first.**

When implementing mechanics: **verify the documentation first.**

When performing calculations: **research the underlying Minecraft values and formulas first.**

When implementing visuals: **find, open, and inspect reference images first.**

When a task is large: **decompose it and delegate independent work to subagents.**

When work can happen independently: **prefer parallel execution.**

When information cannot be verified: **report the uncertainty — never invent it.**
