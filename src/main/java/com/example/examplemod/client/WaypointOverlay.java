package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Waypoints en espacio de pantalla. La punta del pin cae sobre la cara
 * superior del almacen; el nombre va encima y la distancia debajo.
 *
 * Optimizacion: la forma del pin se precalcula una sola vez como lista de
 * tramos (fila inicial, fila final, medio ancho) y todos los pines se emiten
 * en un unico VertexConsumer con un solo flush, en vez de 234 fills por pin.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WaypointOverlay {

    private static final int PIN_W = 18;
    private static final int PIN_H = 26;
    private static final int MARGIN = 26;
    private static final double AIM = 30.0D;

    private static final int[][] OUTLINE = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, 1}, {-1, 1}, {1, -1}};

    /** Cuerpo del pin y contorno, ya fusionados en tramos verticales. */
    private static final int[][] BODY = buildRuns(halves());
    private static final int[][] BORDER = buildRuns(borders());

    private static final List<Label> LABELS = new ArrayList<>();
    private static final Map<UUID, Texts> TEXTS = new HashMap<>();

    private static final class Texts {
        private final String initial;
        private final String name;
        private final int index;
        private final String badge;
        private final String label;

        private Texts(String initial, String name, int index, String badge, String label) {
            this.initial = initial;
            this.name = name;
            this.index = index;
            this.badge = badge;
            this.label = label;
        }
    }

    private static final class Label {
        private final ClientDeathHistory.Active active;
        private final int x;
        private final int y;
        private final double distance;
        private final boolean aimed;
        private final String badge;
        private final String name;
        private final String distanceText;
        private final int badgeColor;
        private int textWidth;
        private int plateX;
        private int plateY;

        private Label(ClientDeathHistory.Active active, int x, int y, double distance,
                      boolean aimed, String badge, String name, String distanceText, int badgeColor) {
            this.active = active;
            this.x = x;
            this.y = y;
            this.distance = distance;
            this.aimed = aimed;
            this.badge = badge;
            this.name = name;
            this.distanceText = distanceText;
            this.badgeColor = badgeColor;
        }
    }

    private WaypointOverlay() {
    }

    // ------------------------------------------------------------ geometria

    private static int[] halves() {
        int[] out = new int[PIN_H];
        for (int row = 0; row < PIN_H; row++) {
            out[row] = half(row);
        }
        return out;
    }

    /** Union exacta de los 8 desplazamientos del contorno. */
    private static int[] borders() {
        int[] body = halves();
        int[] out = new int[PIN_H];
        for (int row = 0; row < PIN_H; row++) {
            int widest = 0;
            for (int delta = -1; delta <= 1; delta++) {
                int near = row + delta;
                if (near >= 0 && near < PIN_H) {
                    widest = Math.max(widest, body[near]);
                }
            }
            out[row] = widest <= 0 ? 0 : widest + 1;
        }
        return out;
    }

    /** Convierte anchos por fila en tramos con el mismo ancho. */
    private static int[][] buildRuns(int[] extents) {
        List<int[]> runs = new ArrayList<>();
        int row = 0;

        while (row < PIN_H) {
            int extent = extents[row];
            if (extent <= 0) {
                row++;
                continue;
            }
            int end = row;
            while (end + 1 < PIN_H && extents[end + 1] == extent) {
                end++;
            }
            runs.add(new int[]{row, end, extent});
            row = end + 1;
        }

        return runs.toArray(new int[0][]);
    }

    private static int half(int row) {
        if (row <= PIN_W) {
            float radius = PIN_W / 2.0F;
            float dy = row - radius;
            float squared = radius * radius - dy * dy;
            return squared <= 0.0F ? 0 : (int) Math.sqrt(squared);
        }

        float progress = (row - PIN_W) / (float) (PIN_H - PIN_W);
        return (int) ((PIN_W / 2.0F) * (1.0F - progress));
    }

    // -------------------------------------------------------------- render

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        if (ClientDeathHistory.hideAllWaypoints()) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;

        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();

        double centerX = width / 2.0D;
        double centerY = height / 2.0D;

        Camera camera = minecraft.gameRenderer.getMainCamera();
        double eyeX = camera.getPosition().x;
        double eyeY = camera.getPosition().y;
        double eyeZ = camera.getPosition().z;

        double yaw = camera.getYRot() * Mth.DEG_TO_RAD;
        double pitch = camera.getXRot() * Mth.DEG_TO_RAD;

        double forwardX = -Math.sin(yaw) * Math.cos(pitch);
        double forwardY = -Math.sin(pitch);
        double forwardZ = Math.cos(yaw) * Math.cos(pitch);

        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);

        double upX = Math.sin(yaw) * forwardY;
        double upY = -Math.sin(yaw) * forwardX + Math.cos(yaw) * forwardZ;
        double upZ = -Math.cos(yaw) * forwardY;

        double fov = Math.toRadians(minecraft.options.fov().get());
        double tanY = Math.tan(fov / 2.0D);
        double tanX = tanY * (width / (double) Math.max(1, height));

        String dimension = minecraft.level.dimension().location().toString();

        LABELS.clear();

        for (ClientDeathHistory.Active active : ClientDeathHistory.active()) {
            if (active.hidden || !dimension.equals(active.dimension)) {
                continue;
            }
            if (ClientDeathHistory.remainingFor(active.recordId) <= 0L) {
                continue;
            }

            double dx = active.pos.getX() + 0.5D - eyeX;
            double dy = active.pos.getY() + 1.0D - eyeY;
            double dz = active.pos.getZ() + 0.5D - eyeZ;

            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance < 0.05D) {
                continue;
            }

            double depth = dx * forwardX + dy * forwardY + dz * forwardZ;
            if (depth <= 0.08D) {
                continue;
            }

            double sideX = dx * rightX + dz * rightZ;
            double sideY = dx * upX + dy * upY + dz * upZ;

            double screenX = centerX + sideX / depth / tanX * (width / 2.0D);
            double screenY = centerY - sideY / depth / tanY * (height / 2.0D);

            double clampedX = Mth.clamp(screenX, MARGIN, width - MARGIN);
            double clampedY = Mth.clamp(screenY, MARGIN + PIN_H + 16, height - MARGIN);

            boolean offscreen = clampedX != screenX || clampedY != screenY;

            int x = (int) Math.round(clampedX);
            int y = (int) Math.round(clampedY);

            boolean aimed = !offscreen && Math.abs(x - centerX) < AIM && Math.abs(y - centerY) < AIM;

            int color = active.color & 0xFFFFFF;
            Texts texts = texts(active);

            int badgeColor = texts.initial.isEmpty()
                    ? 0xFFFFFFFF
                    : (luminance(color) > 0.55D ? 0xFF10141A : 0xFFFFFFFF);

            LABELS.add(new Label(active, x, y, distance, aimed, texts.badge, texts.label,
                    distance(distance), badgeColor));
        }

        if (LABELS.isEmpty()) {
            return;
        }

        LABELS.sort(Comparator.comparingDouble((Label label) -> label.distance).reversed());

        Matrix4f matrix = graphics.pose().last().pose();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());

        for (Label label : LABELS) {
            int color = 0xFF000000 | (label.active.color & 0xFFFFFF);
            int top = label.y - PIN_H;

            stamp(buffer, matrix, label.x, top, BORDER, 0xFF000000);
            stamp(buffer, matrix, label.x, top, BODY, color);

            if (label.aimed) {
                label.textWidth = font.width(label.name + " \u00B7 " + label.distanceText);
                label.plateX = label.x - label.textWidth / 2 - 5;
                label.plateY = label.y - PIN_H - 18;

                int plateRight = label.plateX + label.textWidth + 10;

                quad(buffer, matrix, label.plateX, label.plateY, plateRight, label.plateY + 14, 0xCC05070A);
                quad(buffer, matrix, label.plateX, label.plateY, plateRight, label.plateY + 1, color);
                quad(buffer, matrix, label.plateX, label.plateY + 13, plateRight, label.plateY + 14, color);
                quad(buffer, matrix, label.plateX, label.plateY, label.plateX + 1, label.plateY + 14, color);
                quad(buffer, matrix, plateRight - 1, label.plateY, plateRight, label.plateY + 14, color);
            }
        }

        graphics.flush();

        for (Label label : LABELS) {
            int top = label.y - PIN_H;

            int badgeWidth = font.width(label.badge);
            outlined(graphics, font, label.badge, label.x - badgeWidth / 2, top + 5, label.badgeColor);

            int distanceWidth = font.width(label.distanceText);
            outlined(graphics, font, label.distanceText, label.x - distanceWidth / 2, label.y + 3, 0xFFFFFFFF);

            if (label.aimed) {
                String text = label.name + " \u00B7 " + label.distanceText;
                outlined(graphics, font, text, label.x - label.textWidth / 2, label.plateY + 3, 0xFFFFFFFF);
            }
        }

        LABELS.clear();
    }

    private static void stamp(VertexConsumer buffer, Matrix4f matrix, int centerX, int top,
                              int[][] shape, int color) {
        for (int[] run : shape) {
            int left = centerX - run[2];
            int right = centerX + run[2] + 1;
            quad(buffer, matrix, left, top + run[0], right, top + run[1] + 1, color);
        }
    }

    /** Mismo orden de vertices que GuiGraphics.fill. */
    private static void quad(VertexConsumer buffer, Matrix4f matrix, int x0, int y0, int x1, int y1, int argb) {
        buffer.vertex(matrix, x0, y0, 0.0F).color(argb).endVertex();
        buffer.vertex(matrix, x0, y1, 0.0F).color(argb).endVertex();
        buffer.vertex(matrix, x1, y1, 0.0F).color(argb).endVertex();
        buffer.vertex(matrix, x1, y0, 0.0F).color(argb).endVertex();
    }

    private static void outlined(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        for (int[] offset : OUTLINE) {
            graphics.drawString(font, text, x + offset[0], y + offset[1], 0xFF000000, false);
        }
        graphics.drawString(font, text, x, y, color, false);
    }

    private static double luminance(int color) {
        double red = ((color >> 16) & 0xFF) / 255.0D;
        double green = ((color >> 8) & 0xFF) / 255.0D;
        double blue = (color & 0xFF) / 255.0D;
        return 0.299D * red + 0.587D * green + 0.114D * blue;
    }

    private static Texts texts(ClientDeathHistory.Active active) {
        String initial = active.initial == null ? "" : active.initial.trim();
        String name = active.name == null ? "" : active.name.trim();

        Texts cached = TEXTS.get(active.recordId);
        if (cached != null && cached.index == active.nameIndex
                && cached.initial.equals(initial) && cached.name.equals(name)) {
            return cached;
        }

        String badge = initial.isEmpty()
                ? Integer.toString(active.nameIndex)
                : (initial.length() > 4 ? initial.substring(0, 4) : initial);
        String label = name.isEmpty() ? Integer.toString(active.nameIndex) : name;

        if (TEXTS.size() > 128) {
            TEXTS.clear();
        }

        Texts built = new Texts(initial, name, active.nameIndex, badge, label);
        TEXTS.put(active.recordId, built);
        return built;
    }

    private static String distance(double blocks) {
        if (blocks < 1000.0D) {
            return (int) Math.round(blocks) + " m";
        }
        return String.format("%.1f km", blocks / 1000.0D);
    }
}