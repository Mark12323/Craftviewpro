package com.aegisvision.cctv;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

final class SvgIcon {
    private static final String RESOURCE_ROOT = "/com/aegisvision/cctv/icons/";

    private SvgIcon() {
    }

    static Node load(String name, double size) {
        String resource = RESOURCE_ROOT + name + ".svg";
        try (InputStream stream = SvgIcon.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalArgumentException("Missing icon resource: " + resource);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);
            Element svg = factory.newDocumentBuilder().parse(stream).getDocumentElement();
            String[] viewBox = svg.getAttribute("viewBox").trim().split("\\s+");
            double width = Double.parseDouble(viewBox[2]);
            double height = Double.parseDouble(viewBox[3]);

            Group drawing = new Group();
            appendChildren(svg, drawing, Style.from(svg, Style.DEFAULT));
            double scale = Math.min(size / width, size / height);
            drawing.getTransforms().add(new Scale(scale, scale, 0, 0));
            drawing.setTranslateX((size - width * scale) / 2);
            drawing.setTranslateY((size - height * scale) / 2);
            drawing.setManaged(false);

            StackPane holder = new StackPane(drawing);
            holder.setMinSize(size, size);
            holder.setPrefSize(size, size);
            holder.setMaxSize(size, size);
            holder.setPickOnBounds(false);
            holder.getStyleClass().add("svg-icon");
            return holder;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load SVG icon " + name, exception);
        }
    }

    private static void appendChildren(Element parent, Group target, Style inherited) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (!(children.item(index) instanceof Element element)) continue;
            Style style = Style.from(element, inherited);
            if ("g".equals(element.getTagName())) {
                Group group = new Group();
                appendChildren(element, group, style);
                target.getChildren().add(group);
                continue;
            }
            Shape shape = switch (element.getTagName()) {
                case "path" -> path(element);
                case "rect" -> rectangle(element);
                case "circle" -> circle(element);
                default -> null;
            };
            if (shape != null) {
                applyStyle(shape, style);
                target.getChildren().add(shape);
            }
        }
    }

    private static SVGPath path(Element element) {
        SVGPath path = new SVGPath();
        path.setContent(element.getAttribute("d"));
        return path;
    }

    private static Rectangle rectangle(Element element) {
        Rectangle rectangle = new Rectangle(
                number(element, "x", 0), number(element, "y", 0),
                number(element, "width", 0), number(element, "height", 0));
        double radius = number(element, "rx", 0) * 2;
        rectangle.setArcWidth(radius);
        rectangle.setArcHeight(radius);
        return rectangle;
    }

    private static Circle circle(Element element) {
        return new Circle(number(element, "cx", 0), number(element, "cy", 0), number(element, "r", 0));
    }

    private static double number(Element element, String name, double fallback) {
        String value = element.getAttribute(name);
        return value.isBlank() ? fallback : Double.parseDouble(value);
    }

    private static void applyStyle(Shape shape, Style style) {
        if ("none".equals(style.fill)) {
            shape.setFill(null);
        } else if ("currentColor".equals(style.fill)) {
            shape.setFill(Color.web("#344054"));
            shape.getStyleClass().add("icon-fill");
        } else {
            shape.setFill(Color.web(style.fill));
        }
        if ("none".equals(style.stroke)) {
            shape.setStroke(null);
        } else if ("currentColor".equals(style.stroke)) {
            shape.setStroke(Color.web("#344054"));
            shape.getStyleClass().add("icon-stroke");
        } else {
            shape.setStroke(Color.web(style.stroke));
        }
        shape.setStrokeWidth(style.strokeWidth);
        shape.setStrokeLineCap(switch (style.lineCap) {
            case "round" -> StrokeLineCap.ROUND;
            case "square" -> StrokeLineCap.SQUARE;
            default -> StrokeLineCap.BUTT;
        });
        shape.setStrokeLineJoin(switch (style.lineJoin) {
            case "round" -> StrokeLineJoin.ROUND;
            case "bevel" -> StrokeLineJoin.BEVEL;
            default -> StrokeLineJoin.MITER;
        });
    }

    private record Style(String fill, String stroke, double strokeWidth, String lineCap, String lineJoin) {
        private static final Style DEFAULT = new Style("#000000", "none", 1, "butt", "miter");

        private static Style from(Element element, Style inherited) {
            return new Style(
                    attribute(element, "fill", inherited.fill),
                    attribute(element, "stroke", inherited.stroke),
                    number(element, "stroke-width", inherited.strokeWidth),
                    attribute(element, "stroke-linecap", inherited.lineCap),
                    attribute(element, "stroke-linejoin", inherited.lineJoin));
        }

        private static String attribute(Element element, String name, String fallback) {
            String value = element.getAttribute(name);
            return value.isBlank() ? fallback : value;
        }
    }
}
