package com.tradingdesk.ui;

import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads the local SVG assets under {@code /icons} and {@code /branding} as JavaFX
 * {@link SVGPath} nodes.
 *
 * <p>JavaFX has no SVG image loader, so the {@code d} attribute is extracted from each
 * local SVG and used to build a vector node. That keeps a single set of assets for both
 * the design source and the runtime, and stays crisp at any DPI without raster assets.
 *
 * <p>The {@code fill-rule} is carried across as well: several icons rely on
 * {@code evenodd} to punch holes (the gear's hub, the search ring, the shield tick).
 * Without it those shapes fill solid.
 */
public final class IconLoader {

    private static final String ICON_ROOT = "/icons/";
    private static final String LOGO_PATH = "/branding/algodesk-logo.svg";
    private static final Pattern PATH_DATA = Pattern.compile("d=\"([^\"]+)\"", Pattern.DOTALL);
    private static final Pattern FILL_RULE = Pattern.compile("fill-rule=\"(evenodd|nonzero)\"");
    private static final Map<String, Shape> CACHE = new ConcurrentHashMap<>();

    private IconLoader() {
    }

    /** Path data plus whether the source declared an even-odd fill rule. */
    private record Shape(String pathData, FillRule fillRule) {
    }

    public static SVGPath icon(String name, double size) {
        Shape shape = CACHE.computeIfAbsent(name, key -> parse(readResource(ICON_ROOT + key + ".svg")));
        SVGPath path = new SVGPath();
        path.setContent(shape.pathData());
        path.setFillRule(shape.fillRule());
        path.getStyleClass().add("icon");
        double scale = size / 24.0;
        path.setScaleX(scale);
        path.setScaleY(scale);
        return path;
    }

    public static SVGPath icon(String name) {
        return icon(name, 16.0);
    }

    public static SVGPath logo(double size) {
        Shape shape = CACHE.computeIfAbsent("__logo", key -> parse(readResource(LOGO_PATH)));
        SVGPath path = new SVGPath();
        path.setContent(shape.pathData());
        path.setFillRule(shape.fillRule());
        path.getStyleClass().add("brand-mark");
        double scale = size / 64.0;
        path.setScaleX(scale);
        path.setScaleY(scale);
        return path;
    }

    public static String pathData(String name) {
        return CACHE.computeIfAbsent(name, key -> parse(readResource(ICON_ROOT + key + ".svg"))).pathData();
    }

    public static String logoPathData() {
        return CACHE.computeIfAbsent("__logo", key -> parse(readResource(LOGO_PATH))).pathData();
    }

    private static String readResource(String resource) {
        try (InputStream stream = IconLoader.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing icon resource: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read icon resource: " + resource, e);
        }
    }

    private static Shape parse(String svg) {
        // An icon may declare several <path> elements; each one is a subpath of the same
        // shape, so all "d" attributes are concatenated into a single SVGPath. Taking only
        // the first would silently drop detail (a search handle, CPU pins, clipboard lines).
        Matcher data = PATH_DATA.matcher(svg);
        StringBuilder combined = new StringBuilder();
        while (data.find()) {
            if (combined.length() > 0) {
                combined.append(' ');
            }
            combined.append(data.group(1).replaceAll("\\s+", " ").trim());
        }
        if (combined.length() == 0) {
            throw new IllegalStateException("SVG contains no path data");
        }
        Matcher rule = FILL_RULE.matcher(svg);
        FillRule fillRule = rule.find() && "evenodd".equals(rule.group(1))
                ? FillRule.EVEN_ODD
                : FillRule.NON_ZERO;
        return new Shape(combined.toString(), fillRule);
    }
}
