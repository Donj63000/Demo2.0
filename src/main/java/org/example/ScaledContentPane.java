package org.example;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

public final class ScaledContentPane extends Region {

    private final Group holder = new Group();
    private final Rectangle clip = new Rectangle();
    private final double designWidth;
    private final double designHeight;

    private final BooleanProperty allowUpscale =
            new SimpleBooleanProperty(this, "allowUpscale", false);

    public ScaledContentPane(Node content, double designWidth, double designHeight) {
        if (content == null) throw new IllegalArgumentException("content must not be null");
        if (designWidth <= 0 || designHeight <= 0)
            throw new IllegalArgumentException("Design dimensions must be > 0");

        this.designWidth = designWidth;
        this.designHeight = designHeight;

        holder.getChildren().setAll(content);
        holder.setManaged(false);
        getChildren().add(holder);

        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        setSnapToPixel(true);
    }

    public boolean isAllowUpscale() { return allowUpscale.get(); }
    public void setAllowUpscale(boolean v) { allowUpscale.set(v); }
    public BooleanProperty allowUpscaleProperty() { return allowUpscale; }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) return;

        Insets insets = getInsets();
        double availW = w - insets.getLeft() - insets.getRight();
        double availH = h - insets.getTop()  - insets.getBottom();

        double s = Math.min(availW / designWidth, availH / designHeight);
        if (!isAllowUpscale()) s = Math.min(1.0, s);

        double scaledW = designWidth * s;
        double scaledH = designHeight * s;

        double x = snapPositionX(insets.getLeft() + (availW - scaledW) * 0.5);
        double y = snapPositionY(insets.getTop()  + (availH - scaledH) * 0.5);

        holder.setScaleX(s);
        holder.setScaleY(s);
        holder.relocate(x, y);
    }

    @Override
    protected double computePrefWidth(double height) { return designWidth; }

    @Override
    protected double computePrefHeight(double width) { return designHeight; }
}
