package cn.unfair.ui.mainmenu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class LottieAnimationRenderer {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ResourceLocation ANIMATION = ResourceLocation.of("unfair/lottie/hello.json");
    private final JsonObject layer;
    private final JsonObject groupTransform;
    private final JsonObject pathProperty;
    private final JsonObject trimStart;
    private final JsonObject trimEnd;
    private final JsonObject strokeWidth;
    private final JsonObject strokeOpacity;
    private final float compositionWidth;
    private final float compositionHeight;
    private final float frameRate;
    private final float inPoint;
    private final float duration;
    private long startNanos;

    public LottieAnimationRenderer() {
        JsonObject animation = null;
        JsonObject loadedLayer = null;
        JsonObject loadedGroupTransform = null;
        JsonObject loadedPathProperty = null;
        JsonObject loadedTrimStart = null;
        JsonObject loadedTrimEnd = null;
        JsonObject loadedStrokeWidth = null;
        JsonObject loadedStrokeOpacity = null;
        float width = 500.0F;
        float height = 500.0F;
        float rate = 60.0F;
        float start = 0.0F;
        float length = 1.0F;

        try (InputStream input = Minecraft.getMinecraft().getResourceManager().getResource(ANIMATION).getInputStream()) {
            animation = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            width = number(animation, "w", width);
            height = number(animation, "h", height);
            rate = number(animation, "fr", rate);
            start = number(animation, "ip", start);
            length = Math.max(1.0F, number(animation, "op", start + length) - start);

            JsonArray layers = animation.getAsJsonArray("layers");
            for (JsonElement element : layers) {
                JsonObject candidate = element.getAsJsonObject();
                if (candidate.get("ty").getAsInt() == 4 && candidate.has("shapes")) {
                    loadedLayer = candidate;
                    break;
                }
            }

            if (loadedLayer != null) {
                JsonArray shapes = loadedLayer.getAsJsonArray("shapes");
                for (JsonElement shapeElement : shapes) {
                    JsonObject group = shapeElement.getAsJsonObject();
                    if (!group.has("it")) {
                        continue;
                    }
                    for (JsonElement itemElement : group.getAsJsonArray("it")) {
                        JsonObject item = itemElement.getAsJsonObject();
                        String type = string(item, "ty", "");
                        if ("sh".equals(type)) {
                            loadedPathProperty = item.getAsJsonObject("ks");
                        } else if ("tm".equals(type)) {
                            loadedTrimStart = item.getAsJsonObject("s");
                            loadedTrimEnd = item.getAsJsonObject("e");
                        } else if ("gs".equals(type)) {
                            loadedStrokeWidth = item.getAsJsonObject("w");
                            loadedStrokeOpacity = item.getAsJsonObject("o");
                        } else if ("tr".equals(type)) {
                            loadedGroupTransform = item;
                        }
                    }
                    if (loadedPathProperty != null && loadedTrimStart != null && loadedTrimEnd != null) {
                        break;
                    }
                }
            }
        } catch (Exception exception) {
            LOGGER.error("Unable to load the welcome animation", exception);
        }

        this.layer = loadedLayer;
        this.groupTransform = loadedGroupTransform;
        this.pathProperty = loadedPathProperty;
        this.trimStart = loadedTrimStart;
        this.trimEnd = loadedTrimEnd;
        this.strokeWidth = loadedStrokeWidth;
        this.strokeOpacity = loadedStrokeOpacity;
        this.compositionWidth = width;
        this.compositionHeight = height;
        this.frameRate = rate;
        this.inPoint = start;
        this.duration = length;
        this.reset();
    }

    public void reset() {
        this.startNanos = System.nanoTime();
    }

    public void draw(float centerX, float centerY, float availableSize) {
        this.draw(centerX, centerY, availableSize, 1.0F);
    }

    public void draw(float centerX, float centerY, float availableSize, float opacityMultiplier) {
        if (this.layer == null || this.pathProperty == null || this.trimStart == null || this.trimEnd == null) {
            return;
        }

        float frame = this.inPoint + (float) ((System.nanoTime() - this.startNanos) / 1_000_000_000.0 * this.frameRate) % this.duration;
        ShapeData shape = shapeAt(this.pathProperty, frame);
        if (shape == null || shape.vertices.length < 2) {
            return;
        }

        float start = scalarAt(this.trimStart, frame, 0.0F) / 100.0F;
        float end = scalarAt(this.trimEnd, frame, 100.0F) / 100.0F;
        float width = scalarAt(this.strokeWidth, frame, 8.0F);
        float opacity = scalarAt(this.strokeOpacity, frame, 100.0F) / 100.0F
                * clamp(opacityMultiplier, 0.0F, 1.0F);
        if (opacity <= 0.0F || start == end) {
            return;
        }

        List<Point> points = this.transformedPoints(shape);
        List<List<Point>> trimmed = trim(points, start, end);
        if (trimmed.isEmpty()) {
            return;
        }

        float scale = availableSize / Math.max(this.compositionWidth, this.compositionHeight);
        ScaledResolution resolution = new ScaledResolution(Minecraft.getMinecraft());
        float pixelScale = scale * resolution.getScaleFactor();

        float lineWidth = Math.max(1.0F, width * pixelScale);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_POINT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        GL11.glTranslatef(centerX, centerY, 0.0F);
        GL11.glScalef(scale, scale, 1.0F);
        GL11.glTranslatef(-this.compositionWidth / 2.0F, -this.compositionHeight / 2.0F, 0.0F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_POINT_SMOOTH);
        GL11.glHint(GL11.GL_POINT_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glPointSize(lineWidth);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, opacity);
        // Keep wrapped Trim Paths ranges separate to avoid connecting their endpoints.
        for (List<Point> segment : trimmed) {
            if (segment.size() < 2) {
                continue;
            }
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (Point point : segment) {
                GL11.glVertex2f(point.x, point.y);
            }
            GL11.glEnd();
            // Round joins and endpoints prevent tiny gaps between adjacent sampled curve segments.
            GL11.glBegin(GL11.GL_POINTS);
            for (Point point : segment) {
                GL11.glVertex2f(point.x, point.y);
            }
            GL11.glEnd();
        }
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    private List<Point> transformedPoints(ShapeData shape) {
        List<Point> points = new ArrayList<>();
        float[] groupPosition = vector(this.groupTransform, "p", new float[]{0.0F, 0.0F});
        float[] groupAnchor = vector(this.groupTransform, "a", new float[]{0.0F, 0.0F});
        float[] groupScale = vector(this.groupTransform, "s", new float[]{100.0F, 100.0F});
        float groupRotation = scalarProperty(this.groupTransform, "r", 0.0F);

        float[] layerPosition = vector(this.layer.getAsJsonObject("ks"), "p", new float[]{0.0F, 0.0F});
        float[] layerAnchor = vector(this.layer.getAsJsonObject("ks"), "a", new float[]{0.0F, 0.0F});
        float[] layerScale = vector(this.layer.getAsJsonObject("ks"), "s", new float[]{100.0F, 100.0F});
        float layerRotation = scalarProperty(this.layer.getAsJsonObject("ks"), "r", 0.0F);

        int segmentCount = shape.closed ? shape.vertices.length : shape.vertices.length - 1;
        for (int segment = 0; segment < segmentCount; segment++) {
            int next = (segment + 1) % shape.vertices.length;
            Point from = shape.vertices[segment];
            Point to = shape.vertices[next];
            Point control1 = new Point(from.x + shape.outTangents[segment].x, from.y + shape.outTangents[segment].y);
            Point control2 = new Point(to.x + shape.inTangents[next].x, to.y + shape.inTangents[next].y);
            int steps = 14;
            for (int step = segment == 0 ? 0 : 1; step <= steps; step++) {
                float t = step / (float) steps;
                Point point = cubic(from, control1, control2, to, t);
                point = transform(point, groupPosition, groupAnchor, groupScale, groupRotation);
                point = transform(point, layerPosition, layerAnchor, layerScale, layerRotation);
                points.add(point);
            }
        }
        return points;
    }

    private static Point transform(Point point, float[] position, float[] anchor, float[] scale, float rotation) {
        float x = (point.x - anchor[0]) * scale[0] / 100.0F;
        float y = (point.y - anchor[1]) * scale[1] / 100.0F;
        double radians = Math.toRadians(rotation);
        float rotatedX = (float) (x * Math.cos(radians) - y * Math.sin(radians));
        float rotatedY = (float) (x * Math.sin(radians) + y * Math.cos(radians));
        return new Point(rotatedX + position[0], rotatedY + position[1]);
    }

    private static List<List<Point>> trim(List<Point> points, float start, float end) {
        List<List<Point>> result = new ArrayList<>();
        if (points.size() < 2) {
            return result;
        }

        List<Float> lengths = new ArrayList<>(points.size());
        lengths.add(0.0F);
        float total = 0.0F;
        for (int i = 1; i < points.size(); i++) {
            total += distance(points.get(i - 1), points.get(i));
            lengths.add(total);
        }

        float from = clamp(start, 0.0F, 1.0F) * total;
        float to = clamp(end, 0.0F, 1.0F) * total;
        if (to < from) {
            appendRange(points, lengths, total, from, total, result);
            appendRange(points, lengths, total, 0.0F, to, result);
        } else {
            appendRange(points, lengths, total, from, to, result);
        }
        return result;
    }

    private static void appendRange(List<Point> points, List<Float> lengths, float total, float from, float to, List<List<Point>> output) {
        if (to <= from || total <= 0.0F) {
            return;
        }
        List<Point> segment = new ArrayList<>();
        segment.add(pointAt(points, lengths, from));
        for (int i = 1; i < points.size() - 1; i++) {
            float length = lengths.get(i);
            if (length > from && length < to) {
                segment.add(points.get(i));
            }
        }
        segment.add(pointAt(points, lengths, to));
        output.add(segment);
    }

    private static Point pointAt(List<Point> points, List<Float> lengths, float target) {
        for (int i = 1; i < lengths.size(); i++) {
            float end = lengths.get(i);
            if (end >= target) {
                float start = lengths.get(i - 1);
                float t = end == start ? 0.0F : (target - start) / (end - start);
                Point a = points.get(i - 1);
                Point b = points.get(i);
                return new Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t);
            }
        }
        return points.get(points.size() - 1);
    }

    private static ShapeData shapeAt(JsonObject property, float frame) {
        if (!property.has("k")) {
            return null;
        }
        JsonElement value = property.get("k");
        if (!property.has("a") || property.get("a").getAsInt() == 0) {
            if (value.isJsonObject()) {
                return shape(value.getAsJsonObject());
            }
            return value.isJsonArray() && value.getAsJsonArray().size() > 0
                    && value.getAsJsonArray().get(0).isJsonObject()
                    ? shape(value.getAsJsonArray().get(0).getAsJsonObject()) : null;
        }
        if (!value.isJsonArray() || value.getAsJsonArray().size() == 0) {
            return null;
        }
        JsonArray keyframes = value.getAsJsonArray();
        if (frame <= number(keyframes.get(0).getAsJsonObject(), "t", frame)) {
            return shape(keyframes.get(0).getAsJsonObject().getAsJsonArray("s").get(0).getAsJsonObject());
        }
        for (int i = 0; i < keyframes.size() - 1; i++) {
            JsonObject current = keyframes.get(i).getAsJsonObject();
            JsonObject next = keyframes.get(i + 1).getAsJsonObject();
            float startTime = number(current, "t", frame);
            float endTime = number(next, "t", startTime);
            if (frame <= endTime) {
                ShapeData from = shape(current.getAsJsonArray("s").get(0).getAsJsonObject());
                ShapeData to = shape(next.getAsJsonArray("s").get(0).getAsJsonObject());
                if (from == null || to == null || from.vertices.length != to.vertices.length) {
                    return from;
                }
                float progress = easing(current, next, (frame - startTime) / Math.max(1.0F, endTime - startTime));
                return interpolate(from, to, progress);
            }
        }
        JsonObject last = keyframes.get(keyframes.size() - 1).getAsJsonObject();
        return shape(last.getAsJsonArray("s").get(0).getAsJsonObject());
    }

    private static ShapeData shape(JsonObject source) {
        JsonArray verticesJson = source.getAsJsonArray("v");
        JsonArray incomingJson = source.getAsJsonArray("i");
        JsonArray outgoingJson = source.getAsJsonArray("o");
        Point[] vertices = new Point[verticesJson.size()];
        Point[] incoming = new Point[verticesJson.size()];
        Point[] outgoing = new Point[verticesJson.size()];
        for (int i = 0; i < vertices.length; i++) {
            vertices[i] = point(verticesJson.get(i).getAsJsonArray());
            incoming[i] = point(incomingJson.get(i).getAsJsonArray());
            outgoing[i] = point(outgoingJson.get(i).getAsJsonArray());
        }
        return new ShapeData(vertices, incoming, outgoing, source.has("c") && source.get("c").getAsBoolean());
    }

    private static ShapeData interpolate(ShapeData from, ShapeData to, float progress) {
        Point[] vertices = interpolate(from.vertices, to.vertices, progress);
        Point[] incoming = interpolate(from.inTangents, to.inTangents, progress);
        Point[] outgoing = interpolate(from.outTangents, to.outTangents, progress);
        return new ShapeData(vertices, incoming, outgoing, from.closed);
    }

    private static Point[] interpolate(Point[] from, Point[] to, float progress) {
        Point[] result = new Point[from.length];
        for (int i = 0; i < from.length; i++) {
            result[i] = new Point(from[i].x + (to[i].x - from[i].x) * progress,
                    from[i].y + (to[i].y - from[i].y) * progress);
        }
        return result;
    }

    private static float scalarAt(JsonObject property, float frame, float fallback) {
        if (property == null || !property.has("k")) {
            return fallback;
        }
        JsonElement value = property.get("k");
        if (!property.has("a") || property.get("a").getAsInt() == 0) {
            return scalar(value, fallback);
        }
        if (!value.isJsonArray() || value.getAsJsonArray().size() == 0) {
            return fallback;
        }
        JsonArray keyframes = value.getAsJsonArray();
        JsonObject first = keyframes.get(0).getAsJsonObject();
        if (frame <= number(first, "t", frame)) {
            return keyframeValue(first, fallback);
        }
        for (int i = 0; i < keyframes.size() - 1; i++) {
            JsonObject current = keyframes.get(i).getAsJsonObject();
            JsonObject next = keyframes.get(i + 1).getAsJsonObject();
            float startTime = number(current, "t", frame);
            float endTime = number(next, "t", startTime);
            if (frame <= endTime) {
                if (current.has("h") && current.get("h").getAsInt() == 1) {
                    return keyframeValue(current, fallback);
                }
                float from = keyframeValue(current, fallback);
                float to = keyframeValue(next, from);
                float progress = easing(current, next, (frame - startTime) / Math.max(1.0F, endTime - startTime));
                return from + (to - from) * progress;
            }
        }
        return keyframeValue(keyframes.get(keyframes.size() - 1).getAsJsonObject(), fallback);
    }

    private static float keyframeValue(JsonObject keyframe, float fallback) {
        if (!keyframe.has("s")) {
            return fallback;
        }
        return scalar(keyframe.get("s"), fallback);
    }

    private static float easing(JsonObject current, JsonObject next, float progress) {
        JsonObject outgoing = current.has("o") && current.get("o").isJsonObject() ? current.getAsJsonObject("o") : null;
        JsonObject incoming = next.has("i") && next.get("i").isJsonObject() ? next.getAsJsonObject("i") : null;
        float x1 = easingValue(outgoing, "x", 0.0F);
        float y1 = easingValue(outgoing, "y", 0.0F);
        float x2 = easingValue(incoming, "x", 1.0F);
        float y2 = easingValue(incoming, "y", 1.0F);
        float t = clamp(progress, 0.0F, 1.0F);
        for (int i = 0; i < 8; i++) {
            float x = bezier(0.0F, x1, x2, 1.0F, t);
            float derivative = 3.0F * (1.0F - t) * (1.0F - t) * x1
                    + 6.0F * (1.0F - t) * t * (x2 - x1)
                    + 3.0F * t * t * (1.0F - x2);
            if (Math.abs(derivative) < 0.0001F) {
                break;
            }
            t = clamp(t - (x - progress) / derivative, 0.0F, 1.0F);
        }
        return bezier(0.0F, y1, y2, 1.0F, t);
    }

    private static float easingValue(JsonObject easing, String field, float fallback) {
        if (easing == null || !easing.has(field)) {
            return fallback;
        }
        JsonElement element = easing.get(field);
        return element.isJsonArray() && element.getAsJsonArray().size() > 0
                ? scalar(element.getAsJsonArray().get(0), fallback) : scalar(element, fallback);
    }

    private static float bezier(float a, float b, float c, float d, float t) {
        float inverse = 1.0F - t;
        return inverse * inverse * inverse * a + 3.0F * inverse * inverse * t * b
                + 3.0F * inverse * t * t * c + t * t * t * d;
    }

    private static float[] vector(JsonObject properties, String name, float[] fallback) {
        if (properties == null || !properties.has(name) || !properties.get(name).isJsonObject()) {
            return fallback;
        }
        JsonObject property = properties.getAsJsonObject(name);
        JsonElement value = property.get("k");
        if (value == null || !value.isJsonArray()) {
            return fallback;
        }
        JsonArray array = value.getAsJsonArray();
        float[] result = fallback.clone();
        for (int i = 0; i < Math.min(result.length, array.size()); i++) {
            result[i] = scalar(array.get(i), result[i]);
        }
        return result;
    }

    private static float scalarProperty(JsonObject properties, String name, float fallback) {
        if (properties == null || !properties.has(name) || !properties.get(name).isJsonObject()) {
            return fallback;
        }
        return scalarAt(properties.getAsJsonObject(name), 0.0F, fallback);
    }

    private static float scalar(JsonElement element, float fallback) {
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            return array.size() == 0 ? fallback : scalar(array.get(0), fallback);
        }
        return element.getAsFloat();
    }

    private static float number(JsonObject object, String key, float fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsFloat() : fallback;
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }

    private static Point point(JsonArray array) {
        return new Point(array.get(0).getAsFloat(), array.get(1).getAsFloat());
    }

    private static Point cubic(Point a, Point b, Point c, Point d, float t) {
        float inverse = 1.0F - t;
        float aa = inverse * inverse * inverse;
        float bb = 3.0F * inverse * inverse * t;
        float cc = 3.0F * inverse * t * t;
        float dd = t * t * t;
        return new Point(aa * a.x + bb * b.x + cc * c.x + dd * d.x,
                aa * a.y + bb * b.y + cc * c.y + dd * d.y);
    }

    private static float distance(Point a, Point b) {
        return (float) Math.hypot(b.x - a.x, b.y - a.y);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class ShapeData {
        private final Point[] vertices;
        private final Point[] inTangents;
        private final Point[] outTangents;
        private final boolean closed;

        private ShapeData(Point[] vertices, Point[] inTangents, Point[] outTangents, boolean closed) {
            this.vertices = vertices;
            this.inTangents = inTangents;
            this.outTangents = outTangents;
            this.closed = closed;
        }
    }

    private static final class Point {
        private final float x;
        private final float y;

        private Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
