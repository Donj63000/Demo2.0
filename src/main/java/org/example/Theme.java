package org.example;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class Theme {

    public static final Color DARK_BG        = Color.web("#121212");
    public static final Color DARK_ELEVATION = Color.web("#1e1e1e");
    public static final Color ACCENT         = Color.web("#4facfe");
    public static final Color ACCENT_LIGHT   = Color.web("#00f2fe");
    public static final Color TEXT_DEFAULT   = Color.WHITE;
    public static final Font  MAIN_FONT      = Font.font("Arial", 14);

    public static Background makeBackgroundCover(String imagePath) {
        Image bgImage = new Image(Theme.class.getResourceAsStream(imagePath));
        BackgroundSize bSize = new BackgroundSize(1, 1, true, true, false, true);
        BackgroundImage bImg = new BackgroundImage(bgImage, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT, BackgroundPosition.CENTER, bSize);
        return new Background(bImg);
    }

    public static void styleButton(Button b) {
        if (!b.getStyleClass().contains("app-button")) {
            b.getStyleClass().add("app-button");
        }
        b.setWrapText(false);
        b.setMnemonicParsing(false);
    }

    public static void styleListView(ListView<?> lv) {
        if (!lv.getStyleClass().contains("app-list-view")) {
            lv.getStyleClass().add("app-list-view");
        }
    }

    public static void styleTableView(TableView<?> tv) {
        if (!tv.getStyleClass().contains("app-table-view")) {
            tv.getStyleClass().add("app-table-view");
        }
    }

    public static void styleControl(Control c) {
        c.setStyle("-fx-control-inner-background:#1e1e1e;-fx-background-insets:0;-fx-selection-bar:" + toWebColor(ACCENT) + ";-fx-selection-bar-non-focused:" + toWebColor(ACCENT.darker()) + ";");
    }

    public static String toWebColor(Color c) {
        int r = (int) (c.getRed() * 255);
        int g = (int) (c.getGreen() * 255);
        int b = (int) (c.getBlue() * 255);
        return String.format("#%02X%02X%02X", r, g, b);
    }

    public static void styleTextField(TextField tf) {
        String normal =
                "-fx-background-radius:8;" +
                        "-fx-background-color:#1e1e1e;" +
                        "-fx-text-fill: white;" +
                        "-fx-prompt-text-fill: #bbbbbb;" +
                        "-fx-border-color:" + toWebColor(ACCENT) + ";" +
                        "-fx-border-radius:8;" +
                        "-fx-border-width:1;";
        String focused = "-fx-border-color:" + toWebColor(ACCENT_LIGHT) + ";";
        tf.setStyle(normal);
        tf.focusedProperty().addListener((o, oldV, newV) -> tf.setStyle(newV ? normal + focused : normal));
    }

    public static void styleTextArea(TextArea ta) {
        String normal =
                "-fx-background-radius:8;" +
                        "-fx-background-color:#1e1e1e;" +
                        "-fx-text-fill: white;" +
                        "-fx-prompt-text-fill: #bbbbbb;" +
                        "-fx-border-color:" + toWebColor(ACCENT) + ";" +
                        "-fx-border-radius:8;" +
                        "-fx-border-width:1;";
        String focused = "-fx-border-color:" + toWebColor(ACCENT_LIGHT) + ";";
        ta.setStyle(normal);
        ta.focusedProperty().addListener((o, oldV, newV) -> ta.setStyle(newV ? normal + focused : normal));
    }

    public static void styleCapsuleLabel(Label label, String startColor, String endColor) {
        label.setFont(Font.font("Roboto", FontWeight.BOLD, 16));
        label.setTextFill(Color.WHITE);
        label.setPadding(new Insets(6, 14, 6, 14));
        label.setAlignment(Pos.CENTER);
        LinearGradient gradient = new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, new Stop(0, Color.web(startColor)), new Stop(1, Color.web(endColor)));
        label.setBackground(new Background(new BackgroundFill(gradient, new CornerRadii(18), Insets.EMPTY)));
        label.setEffect(new DropShadow(8, Color.rgb(0, 0, 0, 0.3)));
    }

    public static void styleDialogRoot(Region root) {
        if (root == null) {
            return;
        }
        LinearGradient gradient = new LinearGradient(
                0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#0a1f44")),
                new Stop(1, Color.web("#1565c0"))
        );
        root.setBackground(new Background(
                new BackgroundFill(gradient, new CornerRadii(14), Insets.EMPTY)
        ));
        root.setBorder(new Border(new BorderStroke(
                Color.web("#5fa8ff"),
                BorderStrokeStyle.SOLID,
                new CornerRadii(14),
                new BorderWidths(1)
        )));
        if (root.getPadding() == null || root.getPadding().equals(Insets.EMPTY)) {
            root.setPadding(new Insets(12));
        }
        root.setEffect(new DropShadow(18, Color.rgb(0, 0, 0, 0.3)));
    }
}
