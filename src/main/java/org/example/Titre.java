package org.example;

import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;
import javafx.util.Duration;

public class Titre {

    private final StackPane root;
    private final Text title;
    private final Rectangle shimmer;

    public Titre() {
        title = new Text("Grande Loterie de la guilde Evolution");
        title.setFont(Font.font("Poppins", FontWeight.EXTRA_BOLD, 38));
        title.setBoundsType(TextBoundsType.VISUAL);
        title.setFill(makeGradient());
        title.setEffect(new DropShadow(20, Color.web("#00000066")));
        title.setCache(true);
        title.setCacheHint(CacheHint.SCALE_AND_ROTATE);
        root = new StackPane(title);
        root.setAlignment(Pos.TOP_LEFT);
        root.setPadding(new Insets(6, 0, 6, 20));
        root.setMaxWidth(StackPane.USE_PREF_SIZE);
        shimmer = new Rectangle();
        shimmer.widthProperty().bind(Bindings.createDoubleBinding(() -> title.getLayoutBounds().getWidth() * 1.2, title.layoutBoundsProperty()));
        shimmer.heightProperty().bind(Bindings.createDoubleBinding(() -> title.getLayoutBounds().getHeight() * 2, title.layoutBoundsProperty()));
        shimmer.setRotate(20);
        shimmer.setFill(makeShimmerPaint(0));
        root.getChildren().add(shimmer);
        startShimmer();
    }

    public StackPane getNode() {
        return root;
    }

    private Paint makeGradient() {
        return new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#4facfe")),
                new Stop(1, Color.web("#00f2fe")));
    }

    private Paint makeShimmerPaint(double opacity) {
        return new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.TRANSPARENT),
                new Stop(0.5, Color.web("#ffffff", opacity)),
                new Stop(1, Color.TRANSPARENT));
    }

    private void startShimmer() {
        TranslateTransition slide = new TranslateTransition(Duration.seconds(5), shimmer);
        slide.fromXProperty().bind(Bindings.createDoubleBinding(() -> -title.getLayoutBounds().getWidth(), title.layoutBoundsProperty()));
        slide.toXProperty().bind(Bindings.createDoubleBinding(() -> title.getLayoutBounds().getWidth(), title.layoutBoundsProperty()));
        slide.setCycleCount(TranslateTransition.INDEFINITE);
        slide.setInterpolator(Interpolator.LINEAR);
        slide.play();
        ScaleTransition pulse = new ScaleTransition(Duration.seconds(5), shimmer);
        pulse.setFromX(1);
        pulse.setToX(1.3);
        pulse.setCycleCount(ScaleTransition.INDEFINITE);
        pulse.setAutoReverse(true);
        pulse.play();
        shimmer.fillProperty().bind(Bindings.createObjectBinding(() -> makeShimmerPaint(0.30), shimmer.translateXProperty()));
    }
}
