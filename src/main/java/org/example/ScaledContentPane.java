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
            new SimpleBooleanProperty(this, "allowUpscale", true);

    public ScaledContentPane(Node content, double designWidth, double designHeight) {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (designWidth <= 0 || designHeight <= 0) {
            throw new IllegalArgumentException("Design dimensions must be > 0");
        }

        this.designWidth = designWidth;
        this.designHeight = designHeight;

        // On scale un conteneur neutre (Group), pas le contenu lui-même.
        holder.getChildren().setAll(content);
        holder.setManaged(false);
        getChildren().add(holder);

        // Clip : aucune portion du contenu ne déborde visuellement.
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        // Evite les demi-pixels qui causent du drift pendant les recalculs de layout.
        setSnapToPixel(true);
    }

    public boolean isAllowUpscale() { return allowUpscale.get(); }
    public void setAllowUpscale(boolean value) { allowUpscale.set(value); }
    public BooleanProperty allowUpscaleProperty() { return allowUpscale; }

    @Override
    protected void layoutChildren() {
        final Insets insets = getInsets();
        final double availW = getWidth()  - insets.getLeft() - insets.getRight();
        final double availH = getHeight() - insets.getTop()  - insets.getBottom();
        if (availW <= 0 || availH <= 0) return;

        // Echelle uniforme pour respecter le ratio : s = min(sx, sy)
        double s = Math.min(availW / designWidth, availH / designHeight);

        // Si on ne veut pas agrandir au-delà de la maquette, on borne à 1.0
        if (!isAllowUpscale()) {
            s = Math.min(1.0, s);
        }

        final double scaledW = designWidth  * s;
        final double scaledH = designHeight * s;

        // Centre exact en évitant les positions non entières.
        final double x = snapPositionX(insets.getLeft() + (availW - scaledW) * 0.5);
        final double y = snapPositionY(insets.getTop()  + (availH - scaledH) * 0.5);

        holder.setScaleX(s);
        holder.setScaleY(s);
        holder.relocate(x, y);
    }

    @Override
    protected double computePrefWidth(double height) {
        return designWidth;
    }

    @Override
    protected double computePrefHeight(double width) {
        return designHeight;
    }

    @Override
    protected double computeMinWidth(double height) {
        return 0;
    }

    @Override
    protected double computeMinHeight(double width) {
        return 0;
    }

    @Override
    protected double computeMaxWidth(double height) {
        return Double.MAX_VALUE;
    }

    @Override
    protected double computeMaxHeight(double width) {
        return Double.MAX_VALUE;
    }
}
