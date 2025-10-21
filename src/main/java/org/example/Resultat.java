package org.example;

import javafx.animation.*;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Duration;

/** Capsule flashy qui affiche le résultat de la loterie. */
public class Resultat {

    private final StackPane root  = new StackPane();
    private final Text      icon  = new Text("🎲");
    private final Text      label = new Text("Résultat");
    private final Text      subtitle = new Text("");
    private       String    lastMessage = "?";

    private Timeline gradientLoop;
    private Timeline shimmerLoop;
    private final DropShadow glowShadow = new DropShadow(44, Color.rgb(255, 140, 100, 0.65));

    public Resultat() {
        icon.setFont(Font.font("Segoe UI Emoji", FontWeight.BOLD, 34));
        icon.setFill(Color.WHITE);

        label.setFont(Font.font("Poppins", FontWeight.BOLD, 24));
        label.setFill(Color.WHITE);

        subtitle.setFont(Font.font("Poppins", FontWeight.SEMI_BOLD, 14));
        subtitle.setFill(Color.rgb(255, 245, 245, 0.9));

        VBox textColumn = new VBox(2, label, subtitle);
        textColumn.setAlignment(Pos.CENTER_LEFT);

        HBox content = new HBox(18, icon, textColumn);
        content.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().add(content);
        root.setPadding(new Insets(10, 30, 8, 30));
        root.setTranslateY(-4);
        root.setMaxWidth(Region.USE_PREF_SIZE);

        glowShadow.setInput(new DropShadow(18, Color.rgb(255, 180, 120, 0.55)));
        root.setEffect(glowShadow);

        startAnimatedGradient();
        startShimmer();
    }

    public Pane getNode() {
        return root;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setMessage(String msg) {
        lastMessage = msg;

        boolean win  = msg.toLowerCase().contains("gagn");
        boolean lose = msg.toLowerCase().contains("perdu");

        label.setText(win ? "Victoire !" : lose ? "Raté..." : "Résultat");
        subtitle.setText(msg);

        icon.setText(win ? "🏆" : lose ? "💔" : "🎲");

        Color base = win ? Color.web("#ffe2a6") : lose ? Color.web("#ffc2c2") : Color.WHITE;
        label.setFill(base.interpolate(Color.WHITE, 0.3));
        subtitle.setFill(base.interpolate(Color.WHITE, 0.7));

        glowShadow.setColor(win ? Color.rgb(255, 200, 130, 0.75)
                : lose ? Color.rgb(255, 120, 120, 0.65)
                : Color.rgb(255, 150, 120, 0.6));

        root.setScaleX(0.9);
        root.setScaleY(0.9);
        ScaleTransition pop = new ScaleTransition(Duration.millis(320), root);
        pop.setToX(1);
        pop.setToY(1);
        pop.setInterpolator(Interpolator.EASE_OUT);
        pop.play();
    }

    private void startAnimatedGradient() {
        DoubleProperty offset = new SimpleDoubleProperty(0);
        offset.addListener((obs, oldVal, newVal) -> root.setBackground(new Background(
                new BackgroundFill(makeGradient(newVal.doubleValue()), new CornerRadii(26), Insets.EMPTY))));

        root.setBackground(new Background(
                new BackgroundFill(makeGradient(0), new CornerRadii(26), Insets.EMPTY)));

        gradientLoop = new Timeline(
                new KeyFrame(Duration.ZERO,      new KeyValue(offset, 0)),
                new KeyFrame(Duration.seconds(5.5), new KeyValue(offset, 1))
        );
        gradientLoop.setCycleCount(Animation.INDEFINITE);
        gradientLoop.setAutoReverse(true);
        gradientLoop.play();
    }

    private void startShimmer() {
        shimmerLoop = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(root.opacityProperty(), 0.97)),
                new KeyFrame(Duration.seconds(1.8), new KeyValue(root.opacityProperty(), 1.0))
        );
        shimmerLoop.setCycleCount(Animation.INDEFINITE);
        shimmerLoop.setAutoReverse(true);
        shimmerLoop.play();
    }

    private LinearGradient makeGradient(double offset) {
        return new LinearGradient(offset, 0, 1 + offset, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#ff6b6b")),
                new Stop(0.45, Color.web("#ffb56b")),
                new Stop(0.75, Color.web("#ffd36b")),
                new Stop(1, Color.web("#ff8f5f")));
    }
}
