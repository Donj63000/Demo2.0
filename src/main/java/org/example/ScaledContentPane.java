package org.example;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;

public final class ScaledContentPane extends Region {

    private final StackPane holder = new StackPane();
    private final Rectangle clip = new Rectangle();

    private final double designWidth;
    private final double designHeight;

    private final BooleanProperty allowUpscale = new SimpleBooleanProperty(this, "allowUpscale", false);
    private final ReadOnlyDoubleWrapper scaleFactor = new ReadOnlyDoubleWrapper(this, "scaleFactor", 1.0);

    private final Scale scale = new Scale(1.0, 1.0, 0.0, 0.0);

    public ScaledContentPane(Node content, double designWidth, double designHeight) {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (designWidth <= 0 || designHeight <= 0) {
            throw new IllegalArgumentException("Design dimensions must be > 0");
        }

        this.designWidth = designWidth;
        this.designHeight = designHeight;

        holder.getChildren().setAll(content);
        holder.setManaged(false);
        holder.setMinSize(designWidth, designHeight);
        holder.setPrefSize(designWidth, designHeight);
        holder.setMaxSize(designWidth, designHeight);
        holder.getTransforms().setAll(scale);

        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        getChildren().add(holder);

        setSnapToPixel(true);
        allowUpscale.addListener((obs, ov, nv) -> requestLayout());
    }

    public boolean isAllowUpscale() {
        return allowUpscale.get();
    }

    public void setAllowUpscale(boolean v) {
        allowUpscale.set(v);
    }

    public BooleanProperty allowUpscaleProperty() {
        return allowUpscale;
    }

    public double getScaleFactor() {
        return scaleFactor.get();
    }

    public ReadOnlyDoubleProperty scaleFactorProperty() {
        return scaleFactor.getReadOnlyProperty();
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        Insets insets = getInsets();
        double availW = Math.max(0, w - insets.getLeft() - insets.getRight());
        double availH = Math.max(0, h - insets.getTop() - insets.getBottom());

        double s = Math.min(availW / designWidth, availH / designHeight);
        if (!isAllowUpscale()) {
            s = Math.min(1.0, s);
        }
        if (!Double.isFinite(s) || s <= 0) {
            s = 1.0;
        }

        scaleFactor.set(s);
        scale.setX(s);
        scale.setY(s);

        holder.resize(designWidth, designHeight);

        double scaledW = designWidth * s;
        double scaledH = designHeight * s;

        double x = insets.getLeft() + (availW - scaledW) * 0.5;
        double y = insets.getTop() + (availH - scaledH) * 0.5;

        holder.setLayoutX(snapPositionX(x));
        holder.setLayoutY(snapPositionY(y));
    }

    @Override
    protected double computePrefWidth(double height) {
        return designWidth;
    }

    @Override
    protected double computePrefHeight(double width) {
        return designHeight;
    }
}
