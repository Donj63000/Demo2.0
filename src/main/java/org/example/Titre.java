package org.example;

import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;
import javafx.util.Duration;

public class Titre {

    private static final double H_PADDING = 18;
    private static final double V_PADDING = 6;

    private final StackPane root;
    private final StackPane titleContainer;
    private final Text title;
    private final Text outline;
    private final Rectangle badge;
    private final Rectangle gloss;
    private final Rectangle shimmer;

    public Titre() {
        title = buildTitle();
        outline = buildOutline();
        badge = buildBadge();
        gloss = buildGloss();
        shimmer = buildShimmer();

        titleContainer = new StackPane();
        titleContainer.setAlignment(Pos.TOP_LEFT);
        titleContainer.setPadding(new Insets(V_PADDING, H_PADDING, V_PADDING, H_PADDING));
        titleContainer.setMaxWidth(StackPane.USE_PREF_SIZE);
        titleContainer.setCache(true);
        titleContainer.setCacheHint(CacheHint.SPEED);
        titleContainer.getChildren().addAll(badge, gloss, outline, title, shimmer);

        bindDimensions();

        root = new StackPane(titleContainer);
        root.setAlignment(Pos.TOP_LEFT);
        root.setPadding(new Insets(-12, 0, 0, 14));
        root.setMaxWidth(StackPane.USE_PREF_SIZE);
        root.setCache(true);
        root.setCacheHint(CacheHint.SCALE_AND_ROTATE);
        startShimmer();
    }

    public StackPane getNode() {
        return root;
    }

    private Text buildTitle() {
        Text text = new Text("Grande Loterie de la guilde Evolution");
        text.setFont(Font.font("Poppins", FontWeight.EXTRA_BOLD, 34));
        text.setBoundsType(TextBoundsType.VISUAL);
        text.setFill(makeTitleGradient());
        text.setEffect(createTitleEffect());
        text.setStyle("-fx-font-smoothing-type: lcd;");
        text.setCache(true);
        text.setCacheHint(CacheHint.SCALE_AND_ROTATE);
        return text;
    }

    private Text buildOutline() {
        Text text = new Text();
        text.textProperty().bind(title.textProperty());
        text.fontProperty().bind(title.fontProperty());
        text.setBoundsType(TextBoundsType.VISUAL);
        text.setFill(Color.TRANSPARENT);
        text.setStroke(Color.web("#ffffff80"));
        text.setStrokeWidth(2.8);
        text.setStrokeType(StrokeType.OUTSIDE);
        text.setMouseTransparent(true);
        text.setEffect(new GaussianBlur(3));
        text.setCache(true);
        text.setCacheHint(CacheHint.SCALE);
        return text;
    }

    private Rectangle buildBadge() {
        Rectangle rectangle = new Rectangle();
        rectangle.setArcWidth(36);
        rectangle.setArcHeight(36);
        rectangle.setFill(makeBadgeGradient());
        rectangle.setStroke(Color.web("#5bc0f894"));
        rectangle.setStrokeWidth(1.4);
        DropShadow badgeShadow = new DropShadow(20, Color.web("#00122c80"));
        badgeShadow.setSpread(0.12);
        rectangle.setEffect(badgeShadow);
        rectangle.setMouseTransparent(true);
        return rectangle;
    }

    private Rectangle buildGloss() {
        Rectangle rectangle = new Rectangle();
        rectangle.setArcWidth(36);
        rectangle.setArcHeight(36);
        rectangle.setFill(makeGlossGradient());
        rectangle.setMouseTransparent(true);
        rectangle.setOpacity(0.85);
        rectangle.setEffect(new GaussianBlur(14));
        return rectangle;
    }

    private Rectangle buildShimmer() {
        Rectangle rectangle = new Rectangle();
        rectangle.setRotate(18);
        rectangle.setFill(makeShimmerPaint(0.35));
        rectangle.setBlendMode(BlendMode.ADD);
        rectangle.setMouseTransparent(true);
        return rectangle;
    }

    private void bindDimensions() {
        badge.widthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(380, title.getLayoutBounds().getWidth() + H_PADDING * 2),
                title.layoutBoundsProperty()
        ));
        badge.heightProperty().bind(Bindings.createDoubleBinding(
                () -> title.getLayoutBounds().getHeight() + V_PADDING * 2,
                title.layoutBoundsProperty()
        ));

        gloss.widthProperty().bind(badge.widthProperty());
        gloss.heightProperty().bind(badge.heightProperty());

        shimmer.widthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(200, badge.widthProperty().get() * 0.5),
                badge.widthProperty()
        ));
        shimmer.heightProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(120, badge.heightProperty().get() * 1.15),
                badge.heightProperty()
        ));
    }

    private Paint makeTitleGradient() {
        return new LinearGradient(
                0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#ffe066")),
                new Stop(0.35, Color.web("#f783ac")),
                new Stop(0.7, Color.web("#4facfe")),
                new Stop(1, Color.web("#38d9a9"))
        );
    }

    private DropShadow createTitleEffect() {
        DropShadow inner = new DropShadow(8, Color.web("#00000055"));
        inner.setInput(new InnerShadow(7, Color.web("#00000066")));
        DropShadow outer = new DropShadow(20, Color.web("#1789fc55"));
        outer.setSpread(0.18);
        outer.setInput(inner);
        return outer;
    }

    private Paint makeBadgeGradient() {
        return new LinearGradient(
                0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#10213f")),
                new Stop(0.5, Color.web("#091529")),
                new Stop(1, Color.web("#050a1a"))
        );
    }

    private Paint makeGlossGradient() {
        return new LinearGradient(
                0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#ffffff66")),
                new Stop(0.3, Color.web("#ffffff1a")),
                new Stop(1, Color.TRANSPARENT)
        );
    }

    private Paint makeShimmerPaint(double opacity) {
        return new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.TRANSPARENT),
                new Stop(0.5, Color.web("#ffffff", opacity)),
                new Stop(1, Color.TRANSPARENT));
    }

    private void startShimmer() {
        TranslateTransition slide = new TranslateTransition(Duration.seconds(4.5), shimmer);
        slide.fromXProperty().bind(Bindings.createDoubleBinding(
                () -> -badge.widthProperty().get(),
                badge.widthProperty()
        ));
        slide.toXProperty().bind(Bindings.createDoubleBinding(
                () -> badge.widthProperty().get(),
                badge.widthProperty()
        ));
        slide.setCycleCount(TranslateTransition.INDEFINITE);
        slide.setInterpolator(Interpolator.LINEAR);
        slide.play();

        ScaleTransition pulse = new ScaleTransition(Duration.seconds(5.5), shimmer);
        pulse.setFromX(0.92);
        pulse.setToX(1.16);
        pulse.setCycleCount(ScaleTransition.INDEFINITE);
        pulse.setAutoReverse(true);
        pulse.play();

        shimmer.fillProperty().bind(Bindings.createObjectBinding(
                () -> makeShimmerPaint(0.45),
                shimmer.translateXProperty()
        ));

        ScaleTransition breathe = new ScaleTransition(Duration.seconds(7), titleContainer);
        breathe.setFromX(1);
        breathe.setFromY(1);
        breathe.setToX(1.008);
        breathe.setToY(1.008);
        breathe.setCycleCount(ScaleTransition.INDEFINITE);
        breathe.setAutoReverse(true);
        breathe.setInterpolator(Interpolator.EASE_BOTH);
        breathe.play();
    }
}
