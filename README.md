# NamedLoot (Minecraft 26.2 port)

NamedLoot displays customizable name tags above dropped items, making it easier to see what's around you. Perfect for chaotic combat situations, mining expeditions, or organizing large storage systems.

This branch is a **1:1 feature-complete port** of the original 1.21.11 NamedLoot to **Minecraft 26.2** (Mojang official mappings, default in Loom 1.17+). Every feature from the 1.21.11 release is preserved.

## Status — full feature parity with 1.21.11

- ✅ Project builds with `./gradlew build` (Java 25, Loom 1.17, Fabric API 0.152.2+26.2, ModMenu 20.0.0-beta.3)
- ✅ Both `main` and `client` entrypoints run cleanly on Minecraft 26.2 (verified via `./gradlew runClient` with Xvfb)
- ✅ ModMenu config screen opens without crashing (fixed the `Can only blur once per frame` issue by NOT calling `extractBackground` from inside `extractRenderState`)
- ✅ World render handler subscribes to `LevelRenderEvents.END_MAIN` and renders floating name tags above item entities via the new `OrderedSubmitNodeCollector.submitText(...)` API
- ✅ Text formatting logic preserved 1:1 — `{name}` / `{count}` placeholders, `&`-style color codes
- ✅ Advanced rules engine preserved (rule groups with AND-conditions, Contains / Count < / Count > / Count = condition types, per-rule enable/disable, shared format field per group)
- ✅ **Enchantment detail rendering restored** — enchantment list shown beneath the name tag when `showDetails` is enabled (always or only-on-hover), with optional background box ("Box") or inline background ("Inline") per `useDetailBackgroundBox`
- ✅ Full ModMenu config screen restored 1:1:
  - Two tabs: Default and Advanced
  - Default tab: General section (enable toggle, vertical offset slider + reset, display distance slider + reset), Display Options section (override colors, show details, show details on hover, show name on hover, see through, background color, item background opacity slider, detail background type radio buttons, detail background opacity slider), Text Format & Style section (manual formatting toggle, text format field + reset button, format preview row, color code reference (manual mode) OR name+count color sliders + style checkboxes + reset (automatic mode))
  - Advanced tab: full rule group editor — add/remove rule, add/remove AND-conditions, condition type toggle buttons, value field, shared format field + reset, enable/disable per rule, color code reference panel
  - Mouse-wheel scrolling and click-drag scrollbar when content overflows
  - Color preview squares next to the name/count color sections (automatic mode only)
  - Format preview row showing what the current text format looks like with a sample diamond stack
  - Color code reference panel (rendered inline or on the left depending on screen width)

## Toolchain changes from 1.21.11 → 26.2

| | 1.21.11 | 26.2 |
|---|---|---|
| Minecraft | `1.21.11` | `26.2` |
| Yarn mappings | `1.21.11+build.3` | *(none — Loom 1.17 defaults to Mojang official mappings)* |
| Fabric Loader | `0.18.3` | `0.19.3` |
| Loom plugin | `net.fabricmc.fabric-loom-remap` 1.14-SNAPSHOT | `net.fabricmc.fabric-loom` 1.17-SNAPSHOT |
| Fabric API | `0.140.0+1.21.11` | `0.152.2+26.2` |
| ModMenu | `17.0.0-alpha.1` | `20.0.0-beta.3` |
| Gradle | `9.2.1` | `9.5.1` |
| Java source/target | `21` | `25` |
| Mixin compat level | `JAVA_21` | `JAVA_25` |
| Loom dependency configs | `modImplementation` | `implementation` (Loom 1.17 dropped the `mod*` configurations) |

## Source-level mapping renames (yarn 1.21.x → Mojang 26.2)

The most impactful renames used by this mod:

| Yarn 1.21.x | Mojang 26.2 |
|---|---|
| `MinecraftClient` / `client.world` / `client.textRenderer` | `Minecraft` / `client.level` / `client.font` |
| `MinecraftClient.getInstance()` | `Minecraft.getInstance()` |
| `Text` / `MutableText` | `Component` / `MutableComponent` |
| `Text.literal(...)` / `Text.translatable(...)` | `Component.literal(...)` / `Component.translatable(...)` |
| `Formatting` | `ChatFormatting` |
| `RegistryEntry<T>` | `Holder<T>` |
| `Identifier.of(ns, path)` | `Identifier.fromNamespaceAndPath(ns, path)` *(class kept the name `Identifier`, package is `net.minecraft.resources`)* |
| `Vec3d` / `Box` / `RotationAxis` | `Vec3` / `AABB` / `Axis` (from `com.mojang.math`) |
| `RaycastContext` | `ClipContext` *(moved to `net.minecraft.world.level`)* |
| `MatrixStack` (`client.util.math`) | `PoseStack` (`com.mojang.blaze3d.vertex`) |
| `matrices.push/pop/peek().getPositionMatrix()` | `poseStack.pushPose()/popPose()/last().pose()` |
| `entity.getEntityPos()` / `getLerpedPos(f)` / `getHeight()` | `entity.position()` / `getPosition(f)` / `getBbHeight()` |
| `WorldRenderEvents` (`...rendering.v1.world`) | `LevelRenderEvents` (`...rendering.v1.level`) |
| `WorldRenderContext` | `LevelRenderContext` |
| `context.matrixStack()` | `context.poseStack()` |
| `context.consumers()` / `context.tickDelta()` / `context.camera()` | *(removed from context — use `Minecraft.getInstance()` and `context.levelState().cameraRenderState` instead)* |
| `TextRenderer.draw(..., VertexConsumerProvider, ...)` | `context.submitNodeCollector().submitText(PoseStack, float x, float y, FormattedCharSequence, boolean dropShadow, Font.DisplayMode, int light, int color, int bgColor, int outlineColor)` |
| `TextRenderer.TextLayerType.NORMAL / SEE_THROUGH` | `Font.DisplayMode.NORMAL / SEE_THROUGH / POLYGON_OFFSET` |
| `RenderLayers.textBackground()/textBackgroundSeeThrough()` | `RenderTypes.textBackground()/textBackgroundSeeThrough()` *(note new package: `net.minecraft.client.renderer.rendertype.RenderTypes`)* |
| `vertexConsumer.vertex(matrix, x, y, z).color(r,g,b,a).light(light)` | `vertexConsumer.addVertex(pose, x, y, z).setColor(r,g,b,a).setLight(light)` |
| `Screen.render(GuiGraphics, int, int, float)` | `Screen.extractRenderState(GuiGraphicsExtractor, int, int, float)` |
| `DrawContext` | `GuiGraphicsExtractor` |
| `context.drawTextWithShadow(font, text, x, y, color)` | `graphics.text(font, text, x, y, color)` |
| `context.drawCenteredTextWithShadow(...)` | `graphics.centeredText(...)` |
| `context.fill(...)` / `fillGradient(...)` / `enableScissor(...)` / `disableScissor()` | same names on `GuiGraphicsExtractor` |
| `context.drawStrokedRectangle(x, y, w, h, color)` | `graphics.outline(x, y, w, h, color)` |
| `ButtonWidget.builder(...).dimensions(x,y,w,h).build()` | `Button.builder(text, onPress).pos(x,y).size(w,h).build()` |
| `TextFieldWidget` | `EditBox` (`setText`→`setValue`, `setChangedListener`→`setResponder`) |
| `SliderWidget` (abstract `updateMessage()` + `applyValue()` + `value` field) | `AbstractSliderButton` (same `updateMessage()` + `applyValue()` + `value` field; superclass changed) |
| `Tooltip.of(Text)` | `Tooltip.create(Component)` |
| `Click` (record with `x()`, `y()`, `button()`) | `MouseButtonEvent` (record with `x()`, `y()`, `button()`, `buttonInfo()`) |
| `mouseClicked(Click, boolean)` / `mouseReleased(Click)` / `mouseDragged(Click, dx, dy)` | same method names with `MouseButtonEvent` parameter |
| `MinecraftServer.loadWorld()` | `MinecraftServer.loadLevel()` |
| `Enchantment.getName(holder, level)` | `Enchantment.getFullname(holder, level)` |
| `EnchantmentHelper.getEnchantments(stack).getEnchantments()` returning `Set<RegistryEntry<Enchantment>>` | `stack.getEnchantments().keySet()` returning `Set<Holder<Enchantment>>`; level via `stack.getEnchantments().getLevel(holder)` |
| `this.addDrawableChild(widget)` | `this.addRenderableWidget(widget)` |
| `this.addDrawable((ctx,mx,my,d) -> ...)` | *(no equivalent — capture position in init, draw inline in `extractRenderState`)* |
| `this.client.setScreen(parent)` | `this.minecraft.gui.setScreen(parent)` |

## The `Can only blur once per frame` crash

In Minecraft 26.2, `Gui.extractRenderState` (the vanilla entry point that calls our screen) already calls `Screen.extractBackground` (which triggers `blurBeforeThisStratum`) before invoking our `extractRenderState`. If our `extractRenderState` also calls `extractBackground`, the blur is triggered a second time in the same frame and the game throws `IllegalStateException("Can only blur once per frame")`.

**Fix:** do NOT call `extractBackground(...)` from inside our `extractRenderState(...)`. Just call `super.extractRenderState(...)` (which iterates the renderable widgets) and then do custom drawing. This matches ModMenu's own `ModsScreen` pattern.

## The "deferred draws" pattern

In 1.21.x, `Screen.addDrawable(...)` accepted a lambda `(DrawContext, int, int, float)` that was invoked every frame. In 26.2 there is no direct equivalent — `Renderable.extractRenderState` is the only rendering entry point and it requires implementing a state-extraction pattern.

For the NamedLoot port we use a `List<Consumer<GuiGraphicsExtractor>> deferredDraws` field. Every place in the 1.21.x code that called `addDrawable(lambda)` now instead adds a lambda to `deferredDraws`. Inside our overridden `extractRenderState` we replay all those lambdas against the current `GuiGraphicsExtractor`. This preserves the exact visual behaviour of the 1.21.x screen while fitting the new 26.2 rendering pipeline.

## Running

```bash
# Build
./gradlew build

# Run client (works headless with Xvfb + llvmpipe)
./gradlew runClient

# Run dedicated server
./gradlew runServer
```

You need **JDK 25** installed and exposed via `JAVA_HOME` — Loom 1.17 + Minecraft 26.2 require Java 25 at compile time (`sourceCompatibility`/`targetCompatibility = VERSION_25`, `release = 25`).
