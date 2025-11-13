package org.example;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

import java.util.Objects;

/**
 * Conteneur qui affiche un contenu à une "taille design" (designWidth x designHeight)
 * et le met à l'échelle pour occuper au mieux l'espace disponible en conservant le ratio.
 *
 * - Le contenu est centré et mis à l'échelle (fit-inside).
 * - Aucun débordement n'est visible (clip sur la zone disponible).
 * - Si le contenu est "résizable" (Region/Control), il est forcé à la taille "design".
 * - Les insets/padding du conteneur sont respectés.
 */
public final class ScaledContentPane extends Region {

    // Group porteur du contenu : on scale le holder, pas le contenu directement.
    private final Group holder = new Group();

    // Référence directe au contenu (facilite la maintenance et les contrôles).
    private Node content;

    // Clip appliqué sur la zone disponible (après insets).
    private final Rectangle clip = new Rectangle();

    // Dimensions "design" non négociables (base de calcul du scale).
    private final double designWidth;
    private final double designHeight;

    // Autorise ou non un scale > 1 (upscale).
    private final BooleanProperty allowUpscale =
            new SimpleBooleanProperty(this, "allowUpscale", true);

    // Mémorisation pour éviter des redimensionnements inutiles des nœuds résizables.
    private double lastSizedW = -1;
    private double lastSizedH = -1;

    /**
     * @param content      nœud à afficher
     * @param designWidth  largeur "design" (> 0)
     * @param designHeight hauteur "design" (> 0)
     */
    public ScaledContentPane(Node content, double designWidth, double designHeight) {
        this.content = Objects.requireNonNull(content, "content must not be null");
        if (designWidth <= 0 || designHeight <= 0) {
            throw new IllegalArgumentException("Design dimensions must be > 0");
        }

        this.designWidth = designWidth;
        this.designHeight = designHeight;

        // Holder : unmanaged pour un contrôle total du layout ; on y met le contenu.
        holder.setManaged(false);
        holder.getChildren().setAll(this.content);
        getChildren().add(holder);

        // Clip mis à jour dans layoutChildren pour refléter précisément les insets/tailles.
        setClip(clip);

        // Snapping aux pixels pour limiter le drift/flou.
        setSnapToPixel(true);

        // Toute modification de la politique d'upscale force un nouveau layout.
        allowUpscale.addListener(obs -> requestLayout());
    }

    // --- API contenu ----------------------------------------------------------

    public Node getContent() {
        return content;
    }

    /**
     * Remplace le contenu actuel par un nouveau nœud.
     * La taille "design" reste celle passée au constructeur.
     */
    public void setContent(Node newContent) {
        this.content = Objects.requireNonNull(newContent, "content must not be null");
        holder.getChildren().setAll(this.content);
        lastSizedW = lastSizedH = -1; // forcer un resize du contenu si résizable
        requestLayout();
    }

    // --- Propriété allowUpscale ----------------------------------------------

    public boolean isAllowUpscale() {
        return allowUpscale.get();
    }

    public void setAllowUpscale(boolean v) {
        allowUpscale.set(v);
    }

    public BooleanProperty allowUpscaleProperty() {
        return allowUpscale;
    }

    // --- Layout ---------------------------------------------------------------

    @Override
    protected void layoutChildren() {
        final double w = getWidth();
        final double h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        final Insets insets = getInsets();
        final double availW = Math.max(0, w - insets.getLeft() - insets.getRight());
        final double availH = Math.max(0, h - insets.getTop() - insets.getBottom());

        // (1) Mettre le contenu résizable à la taille "design"
        //     IMPORTANT : un Region/Control placé dans un Group n'est JAMAIS mis automatiquement à taille.
        if (content != null && content.isResizable()) {
            if (Math.abs(lastSizedW - designWidth) > 0.001 || Math.abs(lastSizedH - designHeight) > 0.001) {
                if (content instanceof Region) {
                    Region r = (Region) content;
                    // Evite que le Region n'impose ses min/max et casse notre taille "design".
                    r.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                    r.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                    r.setPrefSize(designWidth, designHeight);
                }
                content.resize(designWidth, designHeight);
                lastSizedW = designWidth;
                lastSizedH = designHeight;
            }
            // Toujours à (0,0) dans le holder (le centrage se fait au niveau du holder).
            content.relocate(0, 0);
        }

        // (2) Calcul du facteur d'échelle (fit-inside avec ratio)
        double scale = Math.min(
                designWidth == 0 ? 1.0 : (availW / designWidth),
                designHeight == 0 ? 1.0 : (availH / designHeight)
        );
        if (!isAllowUpscale()) {
            scale = Math.min(1.0, scale);
        }
        if (!Double.isFinite(scale) || scale <= 0) {
            scale = 1.0;
        }

        final double scaledW = designWidth * scale;
        final double scaledH = designHeight * scale;

        // (3) Centrage + snapping pour éviter les demi-pixels
        final double x = snapPositionX(insets.getLeft() + (availW - scaledW) * 0.5);
        final double y = snapPositionY(insets.getTop()  + (availH - scaledH) * 0.5);

        holder.setScaleX(scale);
        holder.setScaleY(scale);
        holder.relocate(x, y);

        // (4) Clip strictement sur la zone disponible (après insets)
        clip.setX(snapPositionX(insets.getLeft()));
        clip.setY(snapPositionY(insets.getTop()));
        clip.setWidth(snapSizeX(availW));
        clip.setHeight(snapSizeY(availH));
    }

    // --- Sizing API -----------------------------------------------------------

    @Override
    protected double computePrefWidth(double height) {
        final Insets insets = getInsets();
        return insets.getLeft() + designWidth + insets.getRight();
    }

    @Override
    protected double computePrefHeight(double width) {
        final Insets insets = getInsets();
        return insets.getTop() + designHeight + insets.getBottom();
    }

    @Override
    protected double computeMinWidth(double height) {
        // Laisser le parent réduire autant que possible (on clippe de toute manière).
        final Insets insets = getInsets();
        return insets.getLeft() + insets.getRight();
    }

    @Override
    protected double computeMinHeight(double width) {
        final Insets insets = getInsets();
        return insets.getTop() + insets.getBottom();
    }

    @Override
    protected double computeMaxWidth(double height) {
        // Ne pas contraindre la croissance ; l'échelle s'adaptera.
        return Double.MAX_VALUE;
    }

    @Override
    protected double computeMaxHeight(double width) {
        return Double.MAX_VALUE;
    }
}
