package org.example;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Fenêtre optionnelle pour régler la configuration
 * de la roue (ex. nombre de tickets perdants, durée de rotation, etc.).
 */
public class OptionRoue extends Stage {

    // Variable statique : nombre de tickets perdants (100 par défaut).
    private static int losingTickets = 100;

    // Nouvelle variable statique : durée de rotation (50.0 s par défaut)
    private static double spinDuration = 50.0;
    private static boolean adaptLargeScreens = true;
    private static double uiScale = Double.POSITIVE_INFINITY;

    public OptionRoue() {
        setTitle("Options de la roue");

        VBox root = new VBox();
        root.setSpacing(10);
        root.setPadding(new Insets(10));

        // Champ pour le nombre de tickets perdants
        Label lblTickets = new Label("Nombre de tickets perdants :");
        lblTickets.setTextFill(Theme.TEXT_DEFAULT);
        TextField txtTickets = new TextField(String.valueOf(losingTickets));
        Theme.styleTextField(txtTickets);

        // Champ pour la durée de rotation
        Label lblDuration = new Label("Durée de rotation (secondes) :");
        lblDuration.setTextFill(Theme.TEXT_DEFAULT);
        TextField txtDuration = new TextField(String.valueOf(spinDuration));
        Theme.styleTextField(txtDuration);

        CheckBox chkLarge = new CheckBox("Adapter aux grands écrans");
        chkLarge.setTextFill(Theme.TEXT_DEFAULT);
        chkLarge.setSelected(adaptLargeScreens);
        chkLarge.setFocusTraversable(false);

        ComboBox<ScalePreset> scaleBox = new ComboBox<>();
        scaleBox.getItems().addAll(
                new ScalePreset(Double.POSITIVE_INFINITY, "Auto (plein écran)"),
                new ScalePreset(1.0, "100 %"),
                new ScalePreset(1.1, "110 %"),
                new ScalePreset(1.25, "125 %"),
                new ScalePreset(1.35, "135 %"),
                new ScalePreset(1.5, "150 %"),
                new ScalePreset(1.75, "175 %"),
                new ScalePreset(2.0, "200 %")
        );
        scaleBox.setValue(scaleBox.getItems().stream()
                .filter(p -> matchesScale(p.value, uiScale))
                .findFirst()
                .orElse(scaleBox.getItems().get(0)));
        scaleBox.setDisable(!adaptLargeScreens);
        scaleBox.setFocusTraversable(false);

        chkLarge.selectedProperty().addListener((obs, oldVal, newVal) -> scaleBox.setDisable(!newVal));

        HBox scaleRow = new HBox(8, chkLarge, scaleBox);
        scaleRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(scaleBox, Priority.NEVER);

        // Bouton pour enregistrer la valeur
        Button btnSave = new Button("Enregistrer");
        btnSave.setOnAction(e -> {
            try {
                // Lecture du nombre de tickets perdants
                int val = Integer.parseInt(txtTickets.getText().trim());
                if (val >= 0) {
                    losingTickets = val;
                }

                // Lecture de la durée de rotation
                double dur = Double.parseDouble(txtDuration.getText().trim());
                if (dur > 0) {
                    spinDuration = Math.max(1.0, dur);
                    txtDuration.setText(String.valueOf(spinDuration));
                }

                adaptLargeScreens = chkLarge.isSelected();
                ScalePreset selectedPreset = scaleBox.getValue();
                if (adaptLargeScreens && selectedPreset != null) {
                    uiScale = selectedPreset.value;
                } else {
                    uiScale = 1.0;
                }

                // On ferme la fenêtre après sauvegarde
                close();

            } catch (NumberFormatException ex) {
                // Gère l'erreur éventuelle, on peut ignorer ou afficher un message
            }
        });

        // Style Material sur le bouton
        Theme.styleButton(btnSave);

        root.getChildren().addAll(
                lblTickets,
                txtTickets,
                lblDuration,
                txtDuration,
                scaleRow,
                btnSave
        );
        Theme.styleDialogRoot(root);

        Scene scene = new Scene(root, 340, 240);
        setScene(scene);
    }

    // Méthode statique pour récupérer la config du nombre de tickets perdants
    public static int getLosingTickets() {
        return losingTickets;
    }

    // Méthode statique pour récupérer la config de la durée de rotation (en secondes)
    public static double getSpinDuration() {
        return spinDuration;
    }

    public static boolean isAdaptLargeScreens() {
        return adaptLargeScreens;
    }

    public static double getUiScale() {
        return uiScale;
    }

    private static boolean matchesScale(double candidate, double value) {
        if (Double.isInfinite(candidate) || Double.isInfinite(value)) {
            return Double.isInfinite(candidate) && Double.isInfinite(value);
        }
        return Math.abs(candidate - value) < 0.0001;
    }

    private record ScalePreset(double value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
