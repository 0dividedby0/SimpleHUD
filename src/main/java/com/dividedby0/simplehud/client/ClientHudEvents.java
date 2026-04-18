package com.dividedby0.simplehud.client;

import com.dividedby0.simplehud.SimpleHudMod;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public final class ClientHudEvents {
    private static final KeyMapping TOGGLE_OVERLAY = new KeyMapping(
        "key.simplehud.toggle_overlay",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_H,
        "key.categories.simplehud"
    );

    private static final KeyMapping TOGGLE_HAZARD = new KeyMapping(
        "key.simplehud.toggle_hazard",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_J,
        "key.categories.simplehud"
    );

    private static boolean overlayEnabled = true;
    private static boolean hazardEnabled = true;

    private ClientHudEvents() {
    }

    @Mod.EventBusSubscriber(modid = SimpleHudMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBusEvents {
        private ModBusEvents() {
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_OVERLAY);
            event.register(TOGGLE_HAZARD);
        }
    }

    @Mod.EventBusSubscriber(modid = SimpleHudMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeBusEvents {
        private ForgeBusEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) {
                return;
            }

            while (TOGGLE_OVERLAY.consumeClick()) {
                overlayEnabled = !overlayEnabled;
                minecraft.player.displayClientMessage(
                    Component.translatable(overlayEnabled ? "message.simplehud.enabled" : "message.simplehud.disabled"),
                    true
                );
            }

            while (TOGGLE_HAZARD.consumeClick()) {
                hazardEnabled = !hazardEnabled;
                minecraft.player.displayClientMessage(
                    Component.translatable(
                        hazardEnabled ? "message.simplehud.hazard_enabled" : "message.simplehud.hazard_disabled"
                    ),
                    true
                );
            }
        }

        @SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Post event) {
            if (!overlayEnabled) {
                return;
            }

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) {
                return;
            }

            List<Component> lines = buildHudLines(minecraft);
            drawPanel(event.getGuiGraphics(), minecraft.font, lines, 8, 8);
        }

        private static List<Component> buildHudLines(Minecraft minecraft) {
            List<Component> lines = new ArrayList<>();
            lines.add(
                Component.literal(
                    String.format(
                        Locale.ROOT,
                        "X: %.1f  Z: %.1f  Y: %.1f",
                        minecraft.player.getX(),
                        minecraft.player.getZ(),
                        minecraft.player.getY()
                    )
                ).withStyle(ChatFormatting.WHITE)
            );

            String biomeId = minecraft.level
                .getBiome(minecraft.player.blockPosition())
                .unwrapKey()
                .map(key -> formatBiomeName(key.location()))
                .orElse("unknown");
            lines.add(
                Component.literal(biomeId + " | " + formatFacing(minecraft.player.getDirection()))
                    .withStyle(ChatFormatting.WHITE)
            );

            if (hazardEnabled) {
                HitResult hitResult = minecraft.hitResult;
                if (hitResult instanceof BlockHitResult blockHitResult && hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockPos blockPos = blockHitResult.getBlockPos();
                    HazardLevel hazard = getHazardLevel(minecraft.level, blockPos);
                    lines.add(Component.literal("Hazard: " + hazard.label).withStyle(ChatFormatting.WHITE));
                } else {
                    lines.add(Component.literal("Hazard: N/A").withStyle(ChatFormatting.WHITE));
                }
            }

            return lines;
        }

        private static String formatFacing(Direction direction) {
            return switch (direction) {
                case NORTH -> "North";
                case SOUTH -> "South";
                case EAST -> "East";
                case WEST -> "West";
                case UP -> "Up";
                case DOWN -> "Down";
            };
        }

        private static String formatBiomeName(ResourceLocation biomeKey) {
            String path = biomeKey.getPath().replace('_', ' ');
            String[] parts = path.split(" ");
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part.isEmpty()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
            return builder.toString();
        }

        private static HazardLevel getHazardLevel(ClientLevel level, BlockPos groundPos) {
            if (!canSpawnIgnoringLight(level, groundPos)) {
                return HazardLevel.SAFE;
            }

            return isTooDarkForVanillaLightRule(level, groundPos) ? HazardLevel.DANGER : HazardLevel.SUPPRESSED;
        }

        private static boolean canSpawnIgnoringLight(ClientLevel level, BlockPos groundPos) {
            BlockPos spawnPos = groundPos.above();
            BlockPos headPos = spawnPos.above();

            if (!level.getBlockState(groundPos).isFaceSturdy(level, groundPos, Direction.UP)) {
                return false;
            }

            return isClearForSpawn(level, spawnPos) && isClearForSpawn(level, headPos);
        }

        private static boolean isTooDarkForVanillaLightRule(ClientLevel level, BlockPos groundPos) {
            BlockPos spawnPos = groundPos.above();
            int blockLight = level.getBrightness(LightLayer.BLOCK, spawnPos);
            return blockLight <= 7;
        }

        private static boolean isClearForSpawn(ClientLevel level, BlockPos pos) {
            return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
        }

        private static void drawPanel(GuiGraphics graphics, Font font, List<Component> lines, int x, int y) {
            int padding = 3;
            int lineSpacing = 2;
            int lineHeight = font.lineHeight + lineSpacing;
            int panelWidth = 0;
            for (Component line : lines) {
                panelWidth = Math.max(panelWidth, font.width(line));
            }

            int contentHeight = (lines.size() * font.lineHeight) + (Math.max(0, lines.size() - 1) * lineSpacing);
            int panelHeight = contentHeight + (padding * 2);
            int right = x + panelWidth + (padding * 2);
            int bottom = y + panelHeight;

            graphics.fill(x - 1, y - 1, right + 1, bottom + 1, 0x1010161D);
            graphics.fill(x, y - 1, right, y, 0x18161D24);
            graphics.fill(x, bottom, right, bottom + 1, 0x12161D24);
            graphics.fill(x - 1, y, x, bottom, 0x14161D24);
            graphics.fill(right, y, right + 1, bottom, 0x14161D24);
            graphics.fill(x, y, right, bottom, 0x3510161D);

            int textY = y + padding;
            for (Component line : lines) {
                graphics.drawString(font, line, x + padding, textY, 0xFFF4F6F8, true);
                textY += lineHeight;
            }
        }

        private enum HazardLevel {
            SAFE("Safe"),
            SUPPRESSED("Suppressed"),
            DANGER("Danger");

            private final String label;

            HazardLevel(String label) {
                this.label = label;
            }
        }
    }
}
